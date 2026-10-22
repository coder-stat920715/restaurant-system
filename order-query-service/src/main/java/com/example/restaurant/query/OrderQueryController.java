package com.example.restaurant.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/query/orders")
class OrderQueryController {

    private final OrderViewRepository views;
    OrderQueryController(OrderViewRepository views) { this.views = views; }

    @GetMapping("/{id}")
    @PostAuthorize("hasRole('SUPER_ADMIN') or returnObject.customerId() == authentication.name "
        + "or (hasRole('CHEF') and returnObject.locationId() == authentication.token.claims['restaurantLocationId'])")
    OrderView get(@PathVariable String id) {
        return views.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    Page<OrderView> mine(@AuthenticationPrincipal Jwt jwt,
                         @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable p) {
        return views.findByCustomerId(jwt.getSubject(), p);
    }

    @GetMapping("/kitchen-board")
    @PreAuthorize("hasRole('CHEF')")
    Page<OrderView> board(@AuthenticationPrincipal Jwt jwt,
                          @RequestParam(defaultValue = "COMPLETED") String status, Pageable p) {
        return views.findByLocationIdAndStatus(jwt.getClaimAsString("restaurantLocationId"), status, p);
    }
}
