package com.example.payment.psp;

public class PspDeclinedException extends RuntimeException {
    public PspDeclinedException(String message) { super(message); }
}
