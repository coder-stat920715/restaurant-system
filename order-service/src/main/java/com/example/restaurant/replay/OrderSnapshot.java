package com.example.restaurant.replay;

import com.example.restaurant.order.Order;
import com.example.restaurant.order.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full current state of an order. Published on restaurant.events.order with eventType = OrderSnapshot. */
public record OrderSnapshot(UUID orderId, String customerId, String locationId, List<OrderLine> lines,
                            BigDecimal total, String status, Instant createdAt, Instant updatedAt) {

    public static OrderSnapshot from(Order o) {
        return new OrderSnapshot(o.getId(), o.getCustomerId(), o.getRestaurantLocationId(), o.getLines(),
            o.getTotal(), o.getStatus().name(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
