package com.example.kitchen.messaging;

import com.example.kitchen.InventoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
class KitchenCommandListener {

    private static final Logger log = LoggerFactory.getLogger(KitchenCommandListener.class);

    private final ObjectMapper mapper;
    private final InventoryService inventory;
    private final IdempotentProcessor idempotent;

    KitchenCommandListener(ObjectMapper mapper, InventoryService inventory, IdempotentProcessor idempotent) {
        this.mapper = mapper;
        this.inventory = inventory;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = "restaurant.events.saga-kitchen")
    void onCommand(ConsumerRecord<String, String> rec, Acknowledgment ack) throws Exception {
        String type = Hdr.get(rec, "eventType");
        if ("ReserveInventory".equals(type)) {
            JsonNode n = mapper.readTree(rec.value());
            UUID orderId = UUID.fromString(n.get("orderId").asText());
            List<InventoryService.Line> lines = new ArrayList<>();
            for (JsonNode l : n.get("lines")) {
                lines.add(new InventoryService.Line(l.get("menuItemId").asText(), l.get("quantity").asInt()));
            }
            idempotent.once(Hdr.get(rec, "eventId"), () -> inventory.reserve(orderId, lines));
        } else {
            log.warn("Ignoring unknown command {}", type);
        }
        ack.acknowledge();
    }
}
