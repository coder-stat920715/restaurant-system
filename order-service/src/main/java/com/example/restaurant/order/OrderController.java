package com.example.restaurant.order;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
class OrderController {

    record OrderResponse(UUID id, String customerId, String locationId, OrderStatus status,
                         BigDecimal total, List<OrderLine> lines, Instant createdAt, Instant updatedAt) {
        static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getCustomerId(), o.getRestaurantLocationId(),
                o.getStatus(), o.getTotal(), o.getLines(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }

    private final OrderService service;
    OrderController(OrderService service) { this.service = service; }

    @PostMapping
    ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateOrderCommand cmd,
                                             @AuthenticationPrincipal Jwt jwt) {
        UUID id = service.createOrder(cmd, jwt.getSubject());
        return ResponseEntity.accepted().body(Map.of("orderId", id));   // saga continues asynchronously
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(service.get(id));
    }
}
