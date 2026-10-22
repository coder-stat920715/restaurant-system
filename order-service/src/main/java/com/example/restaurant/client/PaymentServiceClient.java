package com.example.restaurant.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Synchronous payment API (e.g. card pre-authorisation). Retry(CircuitBreaker(RateLimiter(Bulkhead(call)))). */
@Component
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);
    private final RestClient rest;

    public PaymentServiceClient(RestClient.Builder builder, @Value("${clients.payment.base-url}") String baseUrl) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.rest = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Retry(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "chargeFallback")
    @RateLimiter(name = "paymentService")
    @Bulkhead(name = "paymentService", type = Bulkhead.Type.SEMAPHORE)
    public PaymentResponse charge(PaymentRequest req) {
        return rest.post().uri("/payments")
            .header("Idempotency-Key", req.idempotencyKey())     // retries cannot double-charge
            .body(req)
            .retrieve()
            .onStatus(s -> s.value() == 402, (rq, rs) -> { throw new PaymentDeclinedException(); })
            .body(PaymentResponse.class);
    }

    PaymentResponse chargeFallback(PaymentRequest req, Throwable t) {
        return switch (t) {
            case CallNotPermittedException e -> {
                log.warn("Payment circuit OPEN for order {}", req.orderId());
                yield new PaymentResponse(null, "DEFERRED");
            }
            case PaymentDeclinedException e -> throw e;
            default -> {
                log.error("Payment failed for order {}", req.orderId(), t);
                yield new PaymentResponse(null, "UNAVAILABLE");
            }
        };
    }
}
