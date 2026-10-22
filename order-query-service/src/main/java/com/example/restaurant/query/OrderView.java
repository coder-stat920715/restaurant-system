package com.example.restaurant.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("order_view")
@CompoundIndex(name = "loc_status", def = "{'locationId': 1, 'status': 1}")
public record OrderView(
        @Id String orderId,
        String customerId,
        String locationId,
        String status,
        BigDecimal total,
        List<LineView> lines,
        Instant createdAt,
        Instant updatedAt) {

    public record LineView(String menuItemId, int quantity, BigDecimal unitPrice) {}
}
