package com.example.restaurant.client;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(UUID orderId, BigDecimal amount, String customerId, String idempotencyKey) {}
