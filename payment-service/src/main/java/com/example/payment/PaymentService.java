package com.example.payment;

import com.example.payment.outbox.OutboxRepository;
import com.example.payment.psp.PspClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final JdbcTemplate jdbc;
    private final OutboxRepository outbox;

    public PaymentService(JdbcTemplate jdbc, OutboxRepository outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    /** Payment row + saga reply (outbox) commit atomically. */
    @Transactional
    public void recordCharge(UUID orderId, BigDecimal amount, PspClient.ChargeResult r) {
        boolean charged = "CHARGED".equals(r.status());
        jdbc.update("""
            INSERT INTO payments(order_id, payment_id, amount, status) VALUES (?, ?, ?, ?)
            ON CONFLICT (order_id) DO NOTHING
            """, orderId, r.paymentId(), amount, charged ? "CHARGED" : "FAILED");

        if (charged) {
            outbox.append("saga-reply", orderId.toString(), "PaymentSucceeded",
                Map.of("orderId", orderId, "paymentId", r.paymentId()));
        } else {
            outbox.append("saga-reply", orderId.toString(), "PaymentFailed",
                Map.of("orderId", orderId, "reason", r.reason() == null ? "Payment failed" : r.reason()));
        }
    }

    public Optional<String> findChargedPaymentId(UUID orderId) {
        List<String> ids = jdbc.query(
            "SELECT payment_id FROM payments WHERE order_id = ? AND status = 'CHARGED'",
            (rs, i) -> rs.getString(1), orderId);
        return ids.stream().findFirst();
    }

    /** Compensation. Always answers PaymentReversed, even if there was nothing to reverse (safe to retry). */
    @Transactional
    public void markReversed(UUID orderId) {
        jdbc.update("UPDATE payments SET status = 'REVERSED', updated_at = now() WHERE order_id = ? AND status = 'CHARGED'",
            orderId);
        outbox.append("saga-reply", orderId.toString(), "PaymentReversed", Map.of("orderId", orderId));
    }
}
