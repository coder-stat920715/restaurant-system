package com.example.restaurant.saga;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Watchdog: sagas stuck in a non-terminal state are compensated. Optimistic locking guards races with replies. */
@Component
class SagaTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutScheduler.class);

    private final SagaRepository sagas;
    private final OrderSagaOrchestrator orchestrator;
    private final Duration timeout;

    SagaTimeoutScheduler(SagaRepository sagas, OrderSagaOrchestrator orchestrator,
                         @Value("${saga.timeout:PT2M}") Duration timeout) {
        this.sagas = sagas;
        this.orchestrator = orchestrator;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${saga.timeout-check-interval:PT30S}")
    void sweep() {
        Instant cutoff = Instant.now().minus(timeout);
        var stuck = sagas.findByStateInAndUpdatedAtBefore(
            EnumSet.of(SagaState.PAYMENT_PENDING, SagaState.INVENTORY_PENDING, SagaState.COMPENSATING_PAYMENT), cutoff);
        for (SagaInstance s : stuck) {
            try {
                log.warn("Saga {} stuck in {} since {}", s.getOrderId(), s.getState(), s.getUpdatedAt());
                orchestrator.timeout(s.getOrderId());
            } catch (RuntimeException e) {          // e.g. optimistic lock: a reply arrived meanwhile
                log.info("Skipping timeout for {}: {}", s.getOrderId(), e.getMessage());
            }
        }
    }
}
