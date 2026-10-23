package com.example.workflow.messaging;

import com.example.workflow.ticket.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Starts the kitchen workflow once the Order saga is COMPLETED (payment taken, stock reserved).
 *   OrderCreated        -> remember location + items
 *   OrderStatusChanged  -> when status = COMPLETED, open a QUEUED ticket
 * Other event types (e.g. OrderSnapshot from a read-model replay) are ignored.
 */
@Component
class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper mapper;
    private final TicketRepository tickets;
    private final IdempotentProcessor idempotent;

    OrderEventListener(ObjectMapper mapper, TicketRepository tickets, IdempotentProcessor idempotent) {
        this.mapper = mapper;
        this.tickets = tickets;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = "restaurant.events.order")
    void onEvent(ConsumerRecord<String, String> rec, Acknowledgment ack) throws Exception {
        String type = Hdr.get(rec, "eventType");
        String eventId = Hdr.get(rec, "eventId");

        if ("OrderCreated".equals(type)) {
            JsonNode n = mapper.readTree(rec.value());
            UUID orderId = UUID.fromString(n.get("orderId").asText());
            String customerId = n.get("customerId").asText();
            String locationId = n.get("locationId").asText();
            String lines = n.get("lines").toString();
            idempotent.once(eventId, () -> tickets.saveSnapshot(orderId, customerId, locationId, lines));

        } else if ("OrderStatusChanged".equals(type)) {
            JsonNode n = mapper.readTree(rec.value());
            if ("COMPLETED".equals(n.path("status").asText())) {
                UUID orderId = UUID.fromString(n.get("orderId").asText());
                idempotent.once(eventId, () -> {
                    if (!tickets.createFromSnapshot(orderId)) {
                        log.warn("No ticket created for order {} (missing snapshot or ticket already exists)", orderId);
                    }
                });
            }
        }
        ack.acknowledge();
    }
}
