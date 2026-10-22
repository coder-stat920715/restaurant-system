package com.example.restaurant.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

/** Controllable fake Payment + Kitchen participants. */
public class TestParticipants {

    private final ObjectMapper mapper;
    private final KafkaTemplate<String, String> kafka;

    private volatile boolean kitchenFails = false;
    private volatile BigDecimal maxAmount = new BigDecimal("500");
    private final List<String> reversedPayments = new CopyOnWriteArrayList<>();

    public TestParticipants(ObjectMapper mapper, KafkaTemplate<String, String> kafka) {
        this.mapper = mapper;
        this.kafka = kafka;
    }

    public void kitchenFails(boolean fail) { this.kitchenFails = fail; }
    public List<String> reversedPayments() { return reversedPayments; }
    public void reset() { kitchenFails = false; reversedPayments.clear(); }

    @KafkaListener(topics = "restaurant.events.saga-payment", groupId = "test-participants")
    void payment(ConsumerRecord<String, String> rec, Acknowledgment ack) throws Exception {
        JsonNode n = mapper.readTree(rec.value());
        String orderId = n.get("orderId").asText();
        switch (header(rec, "eventType")) {
            case "ProcessPayment" -> {
                if (n.get("amount").decimalValue().compareTo(maxAmount) > 0) {
                    sendReply(orderId, "PaymentFailed", UUID.randomUUID().toString(),
                            Map.of("orderId", orderId, "reason", "Amount exceeds limit"));
                } else {
                    sendReply(orderId, "PaymentSucceeded", UUID.randomUUID().toString(),
                            Map.of("orderId", orderId, "paymentId", "pay-" + orderId));
                }
            }
            case "ReversePayment" -> {
                reversedPayments.add(orderId);
                sendReply(orderId, "PaymentReversed", UUID.randomUUID().toString(), Map.of("orderId", orderId));
            }
            default -> { }
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "restaurant.events.saga-kitchen", groupId = "test-participants")
    void kitchen(ConsumerRecord<String, String> rec, Acknowledgment ack) throws Exception {
        String orderId = mapper.readTree(rec.value()).get("orderId").asText();
        if ("ReserveInventory".equals(header(rec, "eventType"))) {
            if (kitchenFails) {
                sendReply(orderId, "InventoryFailed", UUID.randomUUID().toString(),
                        Map.of("orderId", orderId, "reason", "Out of stock"));
            } else {
                sendReply(orderId, "InventoryReserved", UUID.randomUUID().toString(), Map.of("orderId", orderId));
            }
        }
        ack.acknowledge();
    }

    public void sendReply(String orderId, String type, String eventId, Map<String, ?> payload) throws Exception {
        List<Header> headers = List.of(
                new RecordHeader("eventType", type.getBytes(StandardCharsets.UTF_8)),
                new RecordHeader("eventId", eventId.getBytes(StandardCharsets.UTF_8)));
        kafka.send(new ProducerRecord<>("restaurant.events.saga-reply", null, orderId,
                mapper.writeValueAsString(payload), headers)).get();
    }

    private static String header(ConsumerRecord<?, ?> rec, String name) {
        var h = rec.headers().lastHeader(name);
        return h == null ? "" : new String(h.value(), StandardCharsets.UTF_8);
    }
}
