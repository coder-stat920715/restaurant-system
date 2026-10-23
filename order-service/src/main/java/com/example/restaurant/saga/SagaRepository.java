package com.example.restaurant.saga;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaRepository extends JpaRepository<SagaInstance, UUID> {
    List<SagaInstance> findByStateInAndUpdatedAtBefore(Collection<SagaState> states, Instant cutoff);
}
