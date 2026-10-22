package com.example.restaurant.gateway;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain chain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex
                .pathMatchers("/actuator/health/**", "/fallback/**").permitAll()
                .anyExchange().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
            .build();
    }

    /** Rate-limit per authenticated subject; fall back to client IP. */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .filter(p -> p instanceof JwtAuthenticationToken)
            .cast(JwtAuthenticationToken.class)
            .map(t -> t.getToken().getSubject())
            .switchIfEmpty(Mono.fromSupplier(() -> {
                var addr = exchange.getRequest().getRemoteAddress();
                return addr == null ? "anonymous" : addr.getAddress().getHostAddress();
            }));
    }

    /** Relay the verified subject; strip any client-supplied value first. Informational only: services re-validate the JWT. */
    @Bean
    GlobalFilter claimsRelayFilter() {
        return (exchange, chain) -> exchange.getPrincipal()
            .filter(p -> p instanceof JwtAuthenticationToken)
            .cast(JwtAuthenticationToken.class)
            .map(t -> exchange.mutate().request(r -> r.headers(h -> {
                h.remove("X-User-Id");
                h.add("X-User-Id", t.getToken().getSubject());
            })).build())
            .defaultIfEmpty(exchange)
            .flatMap(chain::filter);
    }
}
