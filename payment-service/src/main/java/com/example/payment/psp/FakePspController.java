package com.example.payment.psp;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Stand-in for the external provider so the whole flow runs locally. Remove in production. */
@RestController
@RequestMapping("/psp")
class FakePspController {

    private final BigDecimal declineAbove;
    private final double failureRate;

    FakePspController(@Value("${psp.fake.decline-above:500}") BigDecimal declineAbove,
                      @Value("${psp.fake.failure-rate:0.0}") double failureRate) {
        this.declineAbove = declineAbove;
        this.failureRate = failureRate;
    }

    @PostMapping("/charges")
    ResponseEntity<Map<String, String>> charge(@RequestBody Map<String, Object> body,
                                               @RequestHeader("Idempotency-Key") String key) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "ERROR"));
        }
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        if (amount.compareTo(declineAbove) > 0) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("status", "DECLINED"));
        }
        return ResponseEntity.ok(Map.of("paymentId", "psp-" + key, "status", "CHARGED"));   // deterministic per key
    }

    @PostMapping("/refunds")
    ResponseEntity<Map<String, String>> refund(@RequestBody Map<String, Object> body) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "ERROR"));
        }
        return ResponseEntity.ok(Map.of("status", "REFUNDED"));
    }
}
