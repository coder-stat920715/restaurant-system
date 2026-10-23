package com.example.workflow.security;

import com.example.workflow.ticket.KitchenTicket;
import com.example.workflow.ticket.TicketRepository;
import com.example.workflow.ticket.TicketStatus;
import java.io.Serializable;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * ABAC for kitchen tickets.
 *   READ   : staff of the ticket's location
 *   WRITE  : chef or manager of the location, restaurant open, ticket not yet SERVED (start / ready)
 *   SERVE  : manager only, restaurant open, ticket READY
 *   SUPER_ADMIN: always
 */
@Component
public class TicketPermissionEvaluator implements PermissionEvaluator {

    private final Clock clock;
    private final TicketRepository tickets;
    private final LocationHoursProvider hours;

    public TicketPermissionEvaluator(Clock clock, TicketRepository tickets, LocationHoursProvider hours) {
        this.clock = clock;
        this.tickets = tickets;
        this.hours = hours;
    }

    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        if (!(auth instanceof JwtAuthenticationToken jwtAuth) || !(target instanceof KitchenTicket t)) return false;
        return evaluate(jwtAuth, t, permission.toString());
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable id, String type, Object permission) {
        if (!(auth instanceof JwtAuthenticationToken jwtAuth) || !"KitchenTicket".equals(type)) return false;
        return tickets.findById(UUID.fromString(id.toString()))
            .map(t -> evaluate(jwtAuth, t, permission.toString()))
            .orElse(false);
    }

    private boolean evaluate(JwtAuthenticationToken auth, KitchenTicket t, String permission) {
        Set<String> roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        if (roles.contains("ROLE_SUPER_ADMIN")) return true;
        boolean chef = roles.contains("ROLE_CHEF");
        boolean manager = roles.contains("ROLE_RESTAURANT_MANAGER");
        if (!chef && !manager) return false;

        String userLocation = auth.getToken().getClaimAsString("restaurantLocationId");
        if (!Objects.equals(userLocation, t.locationId())) return false;           // attribute match: same location

        boolean open = hours.isOpen(t.locationId(), clock.instant());              // environment attribute
        return switch (permission) {
            case "READ" -> true;
            case "WRITE" -> open && t.status() != TicketStatus.SERVED;             // resource attribute: state
            case "SERVE" -> manager && open && t.status() == TicketStatus.READY;
            default -> false;
        };
    }
}
