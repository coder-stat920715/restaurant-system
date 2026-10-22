package com.example.restaurant.saga;

import com.example.restaurant.order.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
class SagaListener {

    private final ObjectMapper mapper;
    private final OrderSagaOrchestrator orchestrator;
    private final IdempotentProcessor idempotent;

    SagaListener(ObjectMapper mapper, OrderSagaOrchestrator orchestrator, IdempotentProcessor idempotent) {
        this.mapper = mapper;
        this.orchestrator = orchestrator;
        this.idempotent = idempotent;
    }

    /** Saga starts when our own OrderCreated event comes back from the outbox/CDC pipeline. */
    @KafkaListener(topics = "restaurant.events.order", groupId = "order-service-saga")
    void onOrderEvent(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        if ("OrderCreated".equals(Hdr.get(rec, "eventType"))) {
            var evt = read(rec.value(), OrderEvent.OrderCreated.class);
            idempotent.once(Hdr.get(rec, "eventId"), () -> orchestrator.start(evt));
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "restaurant.events.saga-reply", groupId = "order-service-saga")
    void onReply(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        Class<? extends SagaMessage> type = SagaMessage.typeFor(Hdr.get(rec, "eventType"));
        if (type != null) {
            SagaMessage msg = read(rec.value(), type);
            idempotent.once(Hdr.get(rec, "eventId"), () -> orchestrator.handle(msg));
        }
        ack.acknowledge();
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
