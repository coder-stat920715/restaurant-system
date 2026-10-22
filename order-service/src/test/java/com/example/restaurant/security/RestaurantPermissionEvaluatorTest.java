package com.example.restaurant.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.restaurant.order.Order;
import com.example.restaurant.order.OrderLine;
import com.example.restaurant.order.OrderRepository;
import com.example.restaurant.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RestaurantPermissionEvaluatorTest {

    private final OrderRepository repo = Mockito.mock(OrderRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    private RestaurantPermissionEvaluator evaluator(boolean open) {
        return new RestaurantPermissionEvaluator(clock, repo, (loc, at) -> open);
    }

    private Authentication auth(String sub, String location, String role) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject(sub);
        if (location != null) b.claim("restaurantLocationId", location);
        return new JwtAuthenticationToken(b.build(), List.of(new SimpleGrantedAuthority(role)));
    }

    private Order order(OrderStatus target) {
        var o = Order.create("alice", "loc-1", List.of(new OrderLine("burger", 1, BigDecimal.TEN)), BigDecimal.TEN);
        for (OrderStatus s : switch (target) {
            case ORDER_CREATED -> new OrderStatus[] {};
            case PAYMENT_PENDING -> new OrderStatus[] {OrderStatus.PAYMENT_PENDING};
            case INVENTORY_RESERVED -> new OrderStatus[] {OrderStatus.PAYMENT_PENDING, OrderStatus.INVENTORY_RESERVED};
            case COMPLETED -> new OrderStatus[] {OrderStatus.PAYMENT_PENDING, OrderStatus.INVENTORY_RESERVED, OrderStatus.COMPLETED};
            case CANCELLED -> new OrderStatus[] {OrderStatus.CANCELLED};
        }) o.transitionTo(s);
        return o;
    }

    private boolean can(RestaurantPermissionEvaluator e, Authentication a, Order o, String perm) {
        return e.hasPermission(a, o, perm);
    }

    @Test
    void customerReadsOnlyOwnOrders() {
        var e = evaluator(true);
        var o = order(OrderStatus.PAYMENT_PENDING);
        assertThat(can(e, auth("alice", null, "ROLE_CUSTOMER"), o, "READ")).isTrue();
        assertThat(can(e, auth("bob", null, "ROLE_CUSTOMER"), o, "READ")).isFalse();
        assertThat(can(e, auth("alice", null, "ROLE_CUSTOMER"), o, "WRITE")).isFalse();
    }

    @Test
    void staffAreScopedToTheirLocation() {
        var e = evaluator(true);
        var o = order(OrderStatus.PAYMENT_PENDING);
        assertThat(can(e, auth("c1", "loc-1", "ROLE_CHEF"), o, "READ")).isTrue();
        assertThat(can(e, auth("c2", "loc-2", "ROLE_CHEF"), o, "READ")).isFalse();
        assertThat(can(e, auth("m2", "loc-2", "ROLE_RESTAURANT_MANAGER"), o, "READ")).isFalse();
    }

    @Test
    void chefWritesOnlyReservedOrdersWhileOpen() {
        var chef = auth("c1", "loc-1", "ROLE_CHEF");
        assertThat(can(evaluator(true), chef, order(OrderStatus.INVENTORY_RESERVED), "WRITE")).isTrue();
        assertThat(can(evaluator(false), chef, order(OrderStatus.INVENTORY_RESERVED), "WRITE")).isFalse();
        assertThat(can(evaluator(true), chef, order(OrderStatus.PAYMENT_PENDING), "WRITE")).isFalse();
    }

    @Test
    void managerWritesWhileOpenAndNotOnTerminalOrders() {
        var mgr = auth("m1", "loc-1", "ROLE_RESTAURANT_MANAGER");
        assertThat(can(evaluator(true), mgr, order(OrderStatus.PAYMENT_PENDING), "WRITE")).isTrue();
        assertThat(can(evaluator(false), mgr, order(OrderStatus.PAYMENT_PENDING), "WRITE")).isFalse();
        assertThat(can(evaluator(true), mgr, order(OrderStatus.COMPLETED), "WRITE")).isFalse();
        assertThat(can(evaluator(true), mgr, order(OrderStatus.CANCELLED), "WRITE")).isFalse();
    }

    @Test
    void superAdminAlwaysAllowed() {
        assertThat(can(evaluator(false), auth("root", null, "ROLE_SUPER_ADMIN"), order(OrderStatus.COMPLETED), "WRITE")).isTrue();
    }
}
