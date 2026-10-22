package com.example.restaurant.saga;

import java.util.UUID;

/** Replies from participants (arrive on restaurant.events.saga-reply). */
public sealed interface SagaMessage {
    UUID orderId();

    record PaymentSucceeded(UUID orderId, String paymentId) implements SagaMessage {}
    record PaymentFailed(UUID orderId, String reason) implements SagaMessage {}
    record InventoryReserved(UUID orderId) implements SagaMessage {}
    record InventoryFailed(UUID orderId, String reason) implements SagaMessage {}
    record PaymentReversed(UUID orderId) implements SagaMessage {}

    static Class<? extends SagaMessage> typeFor(String eventType) {
        return switch (eventType) {
            case "PaymentSucceeded" -> PaymentSucceeded.class;
            case "PaymentFailed" -> PaymentFailed.class;
            case "InventoryReserved" -> InventoryReserved.class;
            case "InventoryFailed" -> InventoryFailed.class;
            case "PaymentReversed" -> PaymentReversed.class;
            default -> null;
        };
    }
}
