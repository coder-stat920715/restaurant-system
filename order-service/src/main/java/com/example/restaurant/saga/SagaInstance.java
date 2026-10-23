package com.example.restaurant.saga;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_instance")
public class SagaInstance {
    @Id private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SagaState state;
    private String paymentId;
    private String failureReason;
    private Instant updatedAt = Instant.now();
    @Version private Long version;

    protected SagaInstance() {}
    public SagaInstance(UUID orderId, SagaState state) { this.orderId = orderId; this.state = state; }

    public UUID getOrderId() { return orderId; }
    public SagaState getState() { return state; }
    public void setState(SagaState s) { this.state = s; this.updatedAt = Instant.now(); }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String p) { this.paymentId = p; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String r) { this.failureReason = r; }
    public Instant getUpdatedAt() { return updatedAt; }
}
