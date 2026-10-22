package com.example.restaurant.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Test-only wiring: a CDC stand-in (OutboxRelay) and controllable fake saga participants.
 * Both helpers are nested here so the test sources stay self-contained.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SagaTestSupport {

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        return new OutboxRelay(jdbc, kafka);
    }

    @Bean
    Participants participants(ObjectMapper mapper, KafkaTemplate<String, String> kafka) {
        return new Participants(mapper, kafka);
    }

    /**
     * Stand-in for Debezium in tests: relays committed outbox rows to Kafka using the same topic naming,
     * key and headers as the EventRouter config. (Debezium itself is covered by the local-stack smoke test.)
     */
    public static class OutboxRelay {

        private final JdbcTemplate jdbc;
        private final KafkaTemplate<String, String> kafka;
        private final Map<String, List<String>> published = new ConcurrentHashMap<>();

        public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
            this.jdbc = jdbc;
            this.kafka = kafka;
        }

        @Scheduled(fixedDelay = 200)
        public void relay() {
            var rows = jdbc.queryForList("""
                SELECT id, aggregate_type, aggregate_id, event_type, payload::text AS payload
                FROM outbox_event ORDER BY created_at
                """);
            for (var r : rows) {
                UUID id = (UUID) r.get("id");
                String aggId = (String) r.get("aggregate_id");
                String type = (String) r.get("event_type");
                List<Header> headers = List.of(
                        new RecordHeader("eventType", type.getBytes(StandardCharsets.UTF_8)),
                        new RecordHeader("eventId", id.toString().getBytes(StandardCharsets.UTF_8)));
                kafka.send(new ProducerRecord<>("restaurant.events." + r.get("aggregate_type"), null, aggId,
                        (String) r.get("payload"), headers)).join();
                published.computeIfAbsent(aggId, k -> new CopyOnWriteArrayList<>()).add(type);
                jdbc.update("DELETE FROM outbox_event WHERE id = ?", id);
            }
        }

        public List<String> eventTypesFor(String aggregateId) {
            return published.getOrDefault(aggregateId, List.of());
        }
    }

    /** Controllable fake Payment + Kitchen participants. */
    public static class Participants {

        private final ObjectMapper mapper;
        private final KafkaTemplate<String, String> kafka;

        private volatile boolean kitchenFails = false;
        private volatile BigDecimal maxAmount = new BigDecimal("500");
        private final List<String> reversedPayments = new CopyOnWriteArrayList<>();

        public Participants(ObjectMapper mapper, KafkaTemplate<String, String> kafka) {
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
}
