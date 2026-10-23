package com.example.payment.psp;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Resilient REST client for the external payment provider (PSP).
 * Aspect order is Retry( CircuitBreaker( call ) ). The fallback is declared on @Retry (the OUTERMOST aspect)
 * so every attempt is counted by the circuit breaker and retried first; the fallback only runs when
 * retries are exhausted, the circuit is open, or the PSP declined the charge.
 */
@Component
public class PspClient {

    public record ChargeResult(String paymentId, String status, String reason) {}   // CHARGED | DECLINED | UNAVAILABLE

    private static final Logger log = LoggerFactory.getLogger(PspClient.class);
    private final RestClient rest;

    public PspClient(RestClient.Builder builder, @Value("${psp.base-url}") String baseUrl) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.rest = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Retry(name = "psp", fallbackMethod = "chargeFallback")
    @CircuitBreaker(name = "psp")
    public ChargeResult charge(UUID orderId, BigDecimal amount) {
        return rest.post().uri("/charges")
            .header("Idempotency-Key", orderId.toString())          // retries / redeliveries cannot double-charge
            .body(Map.of("orderId", orderId.toString(), "amount", amount))
            .retrieve()
            .onStatus(s -> s.value() == 402, (rq, rs) -> { throw new PspDeclinedException("Payment declined"); })
            .body(ChargeResult.class);
    }

    public ChargeResult chargeFallback(UUID orderId, BigDecimal amount, Throwable t) {
        if (t instanceof PspDeclinedException d) {
            return new ChargeResult(null, "DECLINED", d.getMessage());
        }
        log.warn("PSP unavailable for order {}: {}", orderId, t.toString());
        return new ChargeResult(null, "UNAVAILABLE", "Payment provider unavailable");
    }

    /** No fallback: a failed refund must surface so the Kafka error handler retries it and finally parks it on the DLT. */
    @Retry(name = "psp")
    @CircuitBreaker(name = "psp")
    public void refund(String paymentId) {
        rest.post().uri("/refunds")
            .header("Idempotency-Key", paymentId)
            .body(Map.of("paymentId", paymentId))
            .retrieve()
            .toBodilessEntity();
    }
}
