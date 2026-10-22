package com.example.restaurant.support;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Stand-in for Debezium in tests: relays committed outbox rows to Kafka using the same topic naming,
 * key and headers as the EventRouter config. (Debezium itself is covered by the local-stack smoke test.)
 */
public class TestOutboxRelay {

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final Map<String, List<String>> published = new ConcurrentHashMap<>();

    public TestOutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
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