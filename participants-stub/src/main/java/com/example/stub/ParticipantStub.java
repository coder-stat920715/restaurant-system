package com.example.stub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Fake Payment + Kitchen participants. Reply format mirrors what the saga listener expects. */
@Component
class ParticipantStub {

    private static final String REPLY_TOPIC = "restaurant.events.saga-reply";

    private final ObjectMapper mapper;
    private final KafkaTemplate<String, String> kafka;
    private final BigDecimal maxAmount;
    private final boolean kitchenFails;

    ParticipantStub(ObjectMapper mapper, KafkaTemplate<String, String> kafka,
                    @Value("${stub.payment.max-amount:500}") BigDecimal maxAmount,
                    @Value("${stub.kitchen.fail:false}") boolean kitchenFails) {
        this.mapper = mapper;
        this.kafka = kafka;
        this.maxAmount = maxAmount;
        this.kitchenFails = kitchenFails;
    }

    @KafkaListener(topics = "restaurant.events.saga-payment")
    void payment(ConsumerRecord<String, String> rec, Acknowledgment ack) throws Exception {
        JsonNode n = mapper.readTree(rec.value());
        String orderId = n.get("orderId").asText();
        switch (header(rec, "eventType")) {
            case "ProcessPayment" -> {
                if (n.get("amount").decimalValue().compareTo(maxAmount) > 0) {
                    reply(orderId, "PaymentFailed", Map.of("orderId", orderId, "reason", "Amount exceeds limit"));
                } else {
                    reply(orderId, "PaymentSucceeded", Map.of("orderId", orderId, "paymentId", "pay-" + UUID.randomUUID()));
                }
            }
            case "ReversePayment" -> reply(orderId, "PaymentReversed", Map.of("orderId", orderId));
            default -> { }
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "restaurant.events.saga-kitchen")
    void kitchen(ConsumerRecord<String, String> rec, Acknowledgment ack) throws Exception {
        String orderId = mapper.readTree(rec.value()).get("orderId").asText();
        if ("ReserveInventory".equals(header(rec, "eventType"))) {
            if (kitchenFails) reply(orderId, "InventoryFailed", Map.of("orderId", orderId, "reason", "Out of stock"));
            else reply(orderId, "InventoryReserved", Map.of("orderId", orderId));
        }
        ack.acknowledge();
    }

    private void reply(String orderId, String type, Map<String, ?> payload) throws Exception {
        List<Header> headers = List.of(
            new RecordHeader("eventType", type.getBytes(StandardCharsets.UTF_8)),
            new RecordHeader("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
        kafka.send(new ProducerRecord<>(REPLY_TOPIC, null, orderId, mapper.writeValueAsString(payload), headers));
    }

    private static String header(ConsumerRecord<?, ?> rec, String name) {
        var h = rec.headers().lastHeader(name);
        return h == null ? "" : new String(h.value(), StandardCharsets.UTF_8);
    }
}
