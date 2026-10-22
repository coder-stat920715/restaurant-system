package com.example.restaurant.saga;

import com.example.restaurant.order.OrderEvent;
import com.example.restaurant.order.OrderService;
import com.example.restaurant.order.OrderStatus;
import com.example.restaurant.outbox.OutboxRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORDER_CREATED -> PAYMENT_PENDING -> INVENTORY_PENDING -> COMPLETED
 * Compensation: PaymentFailed -> CANCEL_ORDER ; InventoryFailed -> REVERSE_PAYMENT -> CANCEL_ORDER
 * Commands go out through the outbox, so state change + command are atomic.
 */
@Component
public class OrderSagaOrchestrator {

    private final SagaRepository sagas;
    private final OutboxRepository outbox;
    private final OrderService orderService;

    public OrderSagaOrchestrator(SagaRepository sagas, OutboxRepository outbox, OrderService orderService) {
        this.sagas = sagas;
        this.outbox = outbox;
        this.orderService = orderService;
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
                send("saga-kitchen", m.orderId(), "ReserveInventory", Map.of("orderId", m.orderId()));
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
                    Map.of("orderId", m.orderId(), "paymentId", saga.getPaymentId()));
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

    private void send(String aggregateType, UUID orderId, String type, Object payload) {
        outbox.append(aggregateType, orderId.toString(), type, payload);
    }
}
