package com.example.restaurant.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Consumer-side copy of the order events (move to a shared module if more consumers appear). */
sealed interface OrderEvents {
    record OrderLine(String menuItemId, int quantity, BigDecimal unitPrice) {}
    record OrderCreated(UUID orderId, String customerId, String locationId,
                        List<OrderLine> lines, BigDecimal total) implements OrderEvents {}
    record OrderStatusChanged(UUID orderId, String status, String reason) implements OrderEvents {}
}
