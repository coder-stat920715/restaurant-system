package com.example.restaurant.saga;

import com.example.restaurant.order.OrderEvent;
import com.example.restaurant.order.OrderRepository;
import com.example.restaurant.order.OrderService;
import com.example.restaurant.order.OrderStatus;
import com.example.restaurant.outbox.OutboxRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORDER_CREATED -> PAYMENT_PENDING -> INVENTORY_PENDING -> COMPLETED
 * Compensation: PaymentFailed -> CANCEL_ORDER ; InventoryFailed -> REVERSE_PAYMENT -> CANCEL_ORDER
 * Timeouts: a step that never answers triggers REVERSE_PAYMENT, then CANCEL_ORDER.
 * Commands go out through the outbox, so state change + command are atomic.
 */
@Component
public class OrderSagaOrchestrator {

    private final SagaRepository sagas;
    private final OutboxRepository outbox;
    private final OrderService orderService;
    private final OrderRepository orders;

    public OrderSagaOrchestrator(SagaRepository sagas, OutboxRepository outbox,
                                 OrderService orderService, OrderRepository orders) {
        this.sagas = sagas;
        this.outbox = outbox;
        this.orderService = orderService;
        this.orders = orders;
    }

    @Transactional
    public void start(OrderEvent.OrderCreated evt) {
        if (sagas.existsById(evt.orderId())) return;           // duplicate delivery
        sagas.save(new SagaInstance(evt.orderId(), SagaState.PAYMENT_PENDING));
        orderService.transition(evt.orderId(), OrderStatus.PAYMENT_PENDING, null);
        send("saga-payment", evt.orderId(), "ProcessPayment",
            Map.of("orderId", evt.orderId(), "amount", evt.total(), "customerId", evt.customerId()));
    }

    @Transactional
    public void handle(SagaMessage msg) {
        SagaInstance saga = sagas.findById(msg.orderId()).orElseThrow();

        SagaState next = switch (msg) {
            case SagaMessage.PaymentSucceeded m when saga.getState() == SagaState.PAYMENT_PENDING -> {
                saga.setPaymentId(m.paymentId());
                var order = orders.findById(m.orderId()).orElseThrow();
                send("saga-kitchen", m.orderId(), "ReserveInventory",
                    Map.of("orderId", m.orderId(), "customerId", order.getCustomerId(), "lines", order.getLines()));
                yield SagaState.INVENTORY_PENDING;
            }
            case SagaMessage.PaymentFailed m when saga.getState() == SagaState.PAYMENT_PENDING -> {
                saga.setFailureReason(m.reason());
                orderService.transition(m.orderId(), OrderStatus.CANCELLED, m.reason());     // CANCEL_ORDER
                yield SagaState.CANCELLED;
            }
            case SagaMessage.InventoryReserved m when saga.getState() == SagaState.INVENTORY_PENDING -> {
                orderService.transition(m.orderId(), OrderStatus.INVENTORY_RESERVED, null);
                orderService.transition(m.orderId(), OrderStatus.COMPLETED, null);
                yield SagaState.COMPLETED;
            }
            case SagaMessage.InventoryFailed m when saga.getState() == SagaState.INVENTORY_PENDING -> {
                saga.setFailureReason(m.reason());
                send("saga-payment", m.orderId(), "ReversePayment",                          // REVERSE_PAYMENT
                    reversePayload(m.orderId(), saga.getPaymentId()));
                yield SagaState.COMPENSATING_PAYMENT;
            }
            case SagaMessage.PaymentReversed m when saga.getState() == SagaState.COMPENSATING_PAYMENT -> {
                orderService.transition(m.orderId(), OrderStatus.CANCELLED, saga.getFailureReason()); // CANCEL_ORDER
                yield SagaState.CANCELLED;
            }
            default -> saga.getState();                      // duplicate or out-of-order reply: ignore
        };
        saga.setState(next);
    }

    /**
     * Called by SagaTimeoutScheduler when a saga has been waiting too long.
     * We always compensate the payment first: a late "PaymentSucceeded" may still be in flight, and
     * commands are ordered per order id, so the reversal runs after any charge for that order.
     */
    @Transactional
    public void timeout(UUID orderId) {
        SagaInstance saga = sagas.findById(orderId).orElse(null);
        if (saga == null) return;

        switch (saga.getState()) {
            case PAYMENT_PENDING, INVENTORY_PENDING -> {
                saga.setFailureReason("Timed out while " + saga.getState());
                send("saga-payment", orderId, "ReversePayment", reversePayload(orderId, saga.getPaymentId()));
                saga.setState(SagaState.COMPENSATING_PAYMENT);
            }
            case COMPENSATING_PAYMENT -> {                   // reversal not acknowledged yet: retry it
                send("saga-payment", orderId, "ReversePayment", reversePayload(orderId, saga.getPaymentId()));
                saga.setState(SagaState.COMPENSATING_PAYMENT);   // refreshes updatedAt
            }
            default -> { }                                   // COMPLETED / CANCELLED: nothing to do
        }
    }

    private Map<String, Object> reversePayload(UUID orderId, String paymentId) {
        Map<String, Object> p = new HashMap<>();
        p.put("orderId", orderId);
        if (paymentId != null) p.put("paymentId", paymentId);
        return p;
    }

    private void send(String aggregateType, UUID orderId, String type, Object payload) {
        outbox.append(aggregateType, orderId.toString(), type, payload);
    }
}
