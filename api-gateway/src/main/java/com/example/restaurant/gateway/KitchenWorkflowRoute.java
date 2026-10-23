package com.example.restaurant.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes the kitchen workflow API. The global JWT check and default rate limiter still apply. */
@Configuration
class KitchenWorkflowRoute {

    @Bean
    RouteLocator kitchenWorkflowRoutes(RouteLocatorBuilder builder,
            @Value("${KITCHEN_WORKFLOW_URL:http://localhost:8087}") String kitchenUrl) {
        return builder.routes()
            .route("kitchen-workflow", r -> r.path("/api/kitchen/**")
                .filters(f -> f.rewritePath("/api/(?<seg>.*)", "/${seg}"))
                .uri(kitchenUrl))
            .build();
    }
}
