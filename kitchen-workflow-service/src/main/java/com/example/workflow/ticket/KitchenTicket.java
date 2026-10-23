package com.example.workflow.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record KitchenTicket(UUID id, UUID orderId, String locationId, TicketStatus status,
                            JsonNode items, Instant createdAt, Instant updatedAt, String updatedBy) {}
