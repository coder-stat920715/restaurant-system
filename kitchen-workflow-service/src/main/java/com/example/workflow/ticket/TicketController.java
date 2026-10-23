package com.example.workflow.ticket;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/kitchen/tickets")
class TicketController {

    private final TicketService service;

    TicketController(TicketService service) {
        this.service = service;
    }

    /** Kitchen board for the caller's own location. Only SUPER_ADMIN may pick another location. */
    @GetMapping
    @PreAuthorize("hasRole('CHEF')")
    List<KitchenTicket> board(@AuthenticationPrincipal Jwt jwt, Authentication auth,
                              @RequestParam(required = false) TicketStatus status,
                              @RequestParam(required = false) String locationId) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        String location = admin && locationId != null ? locationId : jwt.getClaimAsString("restaurantLocationId");
        if (location == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No restaurant location for this user");
        }
        return service.board(location, status);
    }

    @GetMapping("/{id}")
    KitchenTicket get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/start")
    KitchenTicket start(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.start(id, jwt.getSubject());
    }

    @PostMapping("/{id}/ready")
    KitchenTicket ready(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.ready(id, jwt.getSubject());
    }

    @PostMapping("/{id}/serve")
    KitchenTicket serve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.serve(id, jwt.getSubject());
    }
}
