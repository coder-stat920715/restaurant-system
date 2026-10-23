package com.example.payment.messaging;

import com.example.payment.PaymentService;
import com.example.payment.psp.PspClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Handles saga commands from the Order service orchestrator. */
@Component
class PaymentCommandListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);

    private final ObjectMapper mapper;
    private final PspClient psp;
    private final PaymentService payments;
    private final IdempotentProcessor idempotent;

    PaymentCommandListener(ObjectMapper mapper, PspClient psp, PaymentService payments, IdempotentProcessor idempotent) {
        this.mapper = mapper;
        this.psp = psp;
        this.payments = payments;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = "restaurant.events.saga-payment")
    void onCommand(ConsumerRecord<String, String> rec, Acknowledgment ack) throws Exception {
        String type = Hdr.get(rec, "eventType");
        String eventId = Hdr.get(rec, "eventId");
        JsonNode n = mapper.readTree(rec.value());
        UUID orderId = UUID.fromString(n.get("orderId").asText());

        switch (type == null ? "" : type) {
            case "ProcessPayment" -> {
                BigDecimal amount = n.get("amount").decimalValue();
                PspClient.ChargeResult result = psp.charge(orderId, amount);     // remote call stays OUTSIDE the DB tx
                idempotent.once(eventId, () -> payments.recordCharge(orderId, amount, result));
            }
            case "ReversePayment" -> {
                payments.findChargedPaymentId(orderId).ifPresent(psp::refund);
                idempotent.once(eventId, () -> payments.markReversed(orderId));
            }
            default -> log.warn("Ignoring unknown command {}", type);
        }
        ack.acknowledge();
    }
}
