package com.example.restaurant.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Rebuilds read-model documents from OrderSnapshot events (see order-service /admin/replay/orders).
 * Upsert semantics make it idempotent; a snapshot never overwrites a document that live events have already
 * moved forward (compared by updatedAt).
 */
@Component
class OrderSnapshotListener {

    private final OrderViewRepository views;
    private final ObjectMapper mapper;

    OrderSnapshotListener(OrderViewRepository views, ObjectMapper mapper) {
        this.views = views;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "restaurant.events.order", groupId = "order-read-model-snapshots")
    void onEvent(ConsumerRecord<String, String> rec, Acknowledgment ack) throws Exception {
        if ("OrderSnapshot".equals(Hdr.get(rec, "eventType"))) {
            apply(mapper.readValue(rec.value(), OrderSnapshotEvent.class));
        }
        ack.acknowledge();
    }

    void apply(OrderSnapshotEvent s) {
        String id = s.orderId().toString();
        var existing = views.findById(id);
        if (existing.isPresent() && existing.get().updatedAt() != null && s.updatedAt() != null
                && existing.get().updatedAt().isAfter(s.updatedAt())) {
            return;                                           // live data is newer than the snapshot
        }
        List<OrderView.LineView> lines = s.lines() == null ? List.of()
            : s.lines().stream().map(l -> new OrderView.LineView(l.menuItemId(), l.quantity(), l.unitPrice())).toList();
        views.save(new OrderView(id, s.customerId(), s.locationId(), s.status(), s.total(), lines,
            s.createdAt(), s.updatedAt()));
    }
}
