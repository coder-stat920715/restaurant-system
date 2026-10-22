package com.example.restaurant.security;

import com.example.restaurant.order.Order;
import com.example.restaurant.order.OrderRepository;
import com.example.restaurant.order.OrderStatus;
import java.io.Serializable;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class RestaurantPermissionEvaluator implements PermissionEvaluator {

    private final Clock clock;
    private final OrderRepository orders;
    private final LocationHoursProvider hours;

    public RestaurantPermissionEvaluator(Clock clock, OrderRepository orders, LocationHoursProvider hours) {
        this.clock = clock;
        this.orders = orders;
        this.hours = hours;
    }

    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        if (!(auth instanceof JwtAuthenticationToken) || target == null) return false;
        var perm = Permission.valueOf(permission.toString());
        var user = SecurityContextAttributes.from(auth);

        if (user.has("ROLE_SUPER_ADMIN")) return true;                       // RBAC short-circuit

        return switch (target) {
            case Order o -> evaluateOrder(user, o, perm);
            default -> false;
        };
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable id, String type, Object permission) {
        if (!"Order".equals(type)) return false;
        return orders.findById(UUID.fromString(id.toString()))
            .map(o -> hasPermission(auth, o, permission))
            .orElse(false);
    }

    private boolean evaluateOrder(SecurityContextAttributes u, Order o, Permission p) {
        boolean staff = u.has("ROLE_CHEF") || u.has("ROLE_RESTAURANT_MANAGER");

        if (!staff) {   // customers: own orders only
            return u.has("ROLE_CUSTOMER") && o.getCustomerId().equals(u.userId()) && p == Permission.READ;
        }
        // staff: ABAC location match
        if (!Objects.equals(u.locationId(), o.getRestaurantLocationId())) return false;
        if (p == Permission.READ) return true;

        boolean open = hours.isOpen(o.getRestaurantLocationId(), clock.instant());
        if (u.has("ROLE_RESTAURANT_MANAGER")) {
            return open && o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED;
        }
        // chef
        return p == Permission.WRITE && open && o.getStatus() == OrderStatus.INVENTORY_RESERVED;
    }
}
