package com.example.restaurant.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirror of order-service OrderSnapshot (published by the replay endpoint). */
record OrderSnapshotEvent(UUID orderId, String customerId, String locationId, List<OrderEvents.OrderLine> lines,
                          BigDecimal total, String status, Instant createdAt, Instant updatedAt) {}
