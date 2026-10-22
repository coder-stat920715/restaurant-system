package com.example.restaurant.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Events published to restaurant.events.order via the outbox. */
public sealed interface OrderEvent {
    UUID orderId();

    record OrderCreated(UUID orderId, String customerId, String locationId,
                        List<OrderLine> lines, BigDecimal total) implements OrderEvent {}

    record OrderStatusChanged(UUID orderId, String status, String reason) implements OrderEvent {}
}
