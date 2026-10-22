package com.example.restaurant.gateway;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FallbackController {
    @RequestMapping("/fallback/orders")
    ResponseEntity<Map<String, String>> orders() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Order service is temporarily unavailable. Please retry shortly."));
    }
}
