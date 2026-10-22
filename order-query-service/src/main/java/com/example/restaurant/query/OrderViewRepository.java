package com.example.restaurant.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderViewRepository extends MongoRepository<OrderView, String> {
    Page<OrderView> findByLocationIdAndStatus(String locationId, String status, Pageable pageable);
    Page<OrderView> findByCustomerId(String customerId, Pageable pageable);
}
