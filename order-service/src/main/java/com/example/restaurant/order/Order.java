package com.example.restaurant.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orders")
public class Order {

    private static final Map<OrderStatus, Set<OrderStatus>> LEGAL = Map.of(
        OrderStatus.ORDER_CREATED, Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED),
        OrderStatus.PAYMENT_PENDING, Set.of(OrderStatus.INVENTORY_RESERVED, OrderStatus.CANCELLED),
        OrderStatus.INVENTORY_RESERVED, Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));

    @Id private UUID id;
    @Column(nullable = false) private String customerId;
    @Column(nullable = false) private String restaurantLocationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private List<OrderLine> lines;
    @Column(nullable = false) private Instant createdAt;
    private Instant updatedAt;
    @Version private Long version;

    protected Order() {}

    public static Order create(String customerId, String locationId, List<OrderLine> lines, BigDecimal total) {
        var o = new Order();
        o.id = UUID.randomUUID();
        o.customerId = customerId;
        o.restaurantLocationId = locationId;
        o.lines = lines;
        o.total = total;
        o.status = OrderStatus.ORDER_CREATED;
        o.createdAt = o.updatedAt = Instant.now();
        return o;
    }

    public void transitionTo(OrderStatus next) {
        if (!LEGAL.getOrDefault(status, Set.of()).contains(next)) {
            throw new IllegalStateException("Illegal transition " + status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getRestaurantLocationId() { return restaurantLocationId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public List<OrderLine> getLines() { return lines; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
