package com.example.restaurant.saga;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaRepository extends JpaRepository<SagaInstance, UUID> {}
