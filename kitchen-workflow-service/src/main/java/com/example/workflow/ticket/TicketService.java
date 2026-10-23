package com.example.workflow.ticket;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Every state-changing method is guarded by an ABAC check on the concrete ticket (location, role, hours, state). */
@Service
public class TicketService {

    private final TicketRepository tickets;

    public TicketService(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @PreAuthorize("hasPermission(#id, 'KitchenTicket', 'READ')")
    public KitchenTicket get(UUID id) {
        return tickets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** The caller (controller) derives the location from the verified token, never from user input. */
    public List<KitchenTicket> board(String locationId, TicketStatus status) {
        return tickets.listByLocation(locationId, status);
    }

    @Transactional
    @PreAuthorize("hasPermission(#id, 'KitchenTicket', 'WRITE')")
    public KitchenTicket start(UUID id, String user) {
        return move(id, TicketStatus.PREPARING, user);
    }

    @Transactional
    @PreAuthorize("hasPermission(#id, 'KitchenTicket', 'WRITE')")
    public KitchenTicket ready(UUID id, String user) {
        return move(id, TicketStatus.READY, user);
    }

    @Transactional
    @PreAuthorize("hasPermission(#id, 'KitchenTicket', 'SERVE')")
    public KitchenTicket serve(UUID id, String user) {
        return move(id, TicketStatus.SERVED, user);
    }

    private KitchenTicket move(UUID id, TicketStatus target, String user) {
        KitchenTicket t = tickets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!t.status().canMoveTo(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot move ticket from " + t.status() + " to " + target);
        }
        if (!tickets.transition(id, t.status(), target, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket was changed by someone else, reload and retry");
        }
        return tickets.findById(id).orElseThrow();
    }
}
