package com.example.restaurant.saga;

public enum SagaState {
    PAYMENT_PENDING, INVENTORY_PENDING, COMPLETED, COMPENSATING_PAYMENT, CANCELLED
}
