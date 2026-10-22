package com.example.restaurant.saga;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Debezium writes headers as raw bytes; read them explicitly instead of relying on @Header conversion. */
final class Hdr {
    private Hdr() {}
    static String get(ConsumerRecord<?, ?> rec, String name) {
        var h = rec.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
