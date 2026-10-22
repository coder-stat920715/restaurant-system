package com.example.restaurant.client;

public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException() { super("Payment declined"); }
}
