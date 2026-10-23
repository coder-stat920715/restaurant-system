package com.example.workflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.workflow.ticket.KitchenTicket;
import com.example.workflow.ticket.TicketRepository;
import com.example.workflow.ticket.TicketStatus;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TicketPermissionEvaluatorTest {

    private final TicketRepository repo = mock(TicketRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    private TicketPermissionEvaluator evaluator(boolean open) {
        return new TicketPermissionEvaluator(clock, repo, (loc, at) -> open);
    }

    private Authentication user(String role, String location) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("u1");
        if (location != null) b.claim("restaurantLocationId", location);
        return new JwtAuthenticationToken(b.build(), List.of(new SimpleGrantedAuthority(role)));
    }

    private KitchenTicket ticket(TicketStatus status) {
        return new KitchenTicket(UUID.randomUUID(), UUID.randomUUID(), "loc-1", status,
            JsonNodeFactory.instance.arrayNode(), Instant.now(), null, null);
    }

    private boolean can(TicketPermissionEvaluator e, Authentication a, KitchenTicket t, String perm) {
        return e.hasPermission(a, t, perm);
    }

    @Test
    void staffOfAnotherLocationAreDenied() {
        var t = ticket(TicketStatus.QUEUED);
        assertThat(can(evaluator(true), user("ROLE_CHEF", "loc-2"), t, "READ")).isFalse();
        assertThat(can(evaluator(true), user("ROLE_CHEF", "loc-2"), t, "WRITE")).isFalse();
    }

    @Test
    void customersHaveNoKitchenAccess() {
        assertThat(can(evaluator(true), user("ROLE_CUSTOMER", null), ticket(TicketStatus.QUEUED), "READ")).isFalse();
    }

    @Test
    void chefCanPrepareWhileOpenButNotWhenClosed() {
        var chef = user("ROLE_CHEF", "loc-1");
        assertThat(can(evaluator(true), chef, ticket(TicketStatus.QUEUED), "WRITE")).isTrue();
        assertThat(can(evaluator(false), chef, ticket(TicketStatus.QUEUED), "WRITE")).isFalse();
        assertThat(can(evaluator(true), chef, ticket(TicketStatus.QUEUED), "READ")).isTrue();
    }

    @Test
    void servedTicketsAreReadOnly() {
        assertThat(can(evaluator(true), user("ROLE_CHEF", "loc-1"), ticket(TicketStatus.SERVED), "WRITE")).isFalse();
    }

    @Test
    void onlyManagersServeAndOnlyReadyTickets() {
        var manager = user("ROLE_RESTAURANT_MANAGER", "loc-1");
        var chef = user("ROLE_CHEF", "loc-1");
        assertThat(can(evaluator(true), manager, ticket(TicketStatus.READY), "SERVE")).isTrue();
        assertThat(can(evaluator(true), manager, ticket(TicketStatus.PREPARING), "SERVE")).isFalse();
        assertThat(can(evaluator(true), chef, ticket(TicketStatus.READY), "SERVE")).isFalse();
    }

    @Test
    void superAdminAlwaysAllowed() {
        assertThat(can(evaluator(false), user("ROLE_SUPER_ADMIN", null), ticket(TicketStatus.SERVED), "WRITE")).isTrue();
    }

    @Test
    void idBasedLookupLoadsTheTicket() {
        var t = ticket(TicketStatus.QUEUED);
        when(repo.findById(t.id())).thenReturn(Optional.of(t));
        assertThat(evaluator(true).hasPermission(user("ROLE_CHEF", "loc-1"), t.id(), "KitchenTicket", "WRITE")).isTrue();
        assertThat(evaluator(true).hasPermission(user("ROLE_CHEF", "loc-1"), t.id(), "Order", "WRITE")).isFalse();
    }
}
