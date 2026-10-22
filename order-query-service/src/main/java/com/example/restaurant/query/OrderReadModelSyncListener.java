package com.example.restaurant.query;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Projects Debezium outbox events into the Mongo read model. Idempotent and replay-safe. */
@Component
public class OrderReadModelSyncListener {

    private final OrderViewRepository views;
    private final MongoTemplate mongo;
    private final ObjectMapper mapper;

    public OrderReadModelSyncListener(OrderViewRepository views, MongoTemplate mongo, ObjectMapper mapper) {
        this.views = views;
        this.mongo = mongo;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "restaurant.events.order")
    public void onEvent(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        String eventType = Hdr.get(rec, "eventType");
        String eventId = Hdr.get(rec, "eventId");

        try {
            mongo.insert(new ProcessedEvent(eventId, Instant.now()));    // duplicate _id => already applied
        } catch (DuplicateKeyException dup) {
            ack.acknowledge();
            return;
        }

        try {
            apply(eventType, rec.value());
        } catch (Exception e) {
            mongo.remove(query(where("_id").is(eventId)), ProcessedEvent.class);   // let the retry re-apply
            throw e instanceof RuntimeException r ? r : new IllegalStateException(e);
        }
        ack.acknowledge();
    }

    private void apply(String eventType, String json) throws Exception {
        switch (eventType) {
            case "OrderCreated" -> {
                var e = mapper.readValue(json, OrderEvents.OrderCreated.class);
                var now = Instant.now();
                views.save(new OrderView(e.orderId().toString(), e.customerId(), e.locationId(), "ORDER_CREATED",
                    e.total(),
                    e.lines().stream().map(l -> new OrderView.LineView(l.menuItemId(), l.quantity(), l.unitPrice())).toList(),
                    now, now));
            }
            case "OrderStatusChanged" -> {
                var e = mapper.readValue(json, OrderEvents.OrderStatusChanged.class);
                mongo.updateFirst(query(where("_id").is(e.orderId().toString())),
                    new Update().set("status", e.status()).set("updatedAt", Instant.now()), OrderView.class);
            }
            default -> { /* unknown types ignored: forward compatible */ }
        }
    }
}
