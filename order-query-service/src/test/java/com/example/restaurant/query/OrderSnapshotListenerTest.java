package com.example.restaurant.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

class OrderSnapshotListenerTest {

    private final UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final OrderViewRepository views = mock(OrderViewRepository.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final OrderSnapshotListener listener =
        new OrderSnapshotListener(views, new ObjectMapper().findAndRegisterModules());

    private OrderSnapshotEvent snapshot(Instant updatedAt, String status) {
        return new OrderSnapshotEvent(id, "alice", "loc-1",
            List.of(new OrderEvents.OrderLine("burger", 1, BigDecimal.TEN)), BigDecimal.TEN, status,
            Instant.parse("2026-01-01T10:00:00Z"), updatedAt);
    }

    private OrderView existing(Instant updatedAt, String status) {
        return new OrderView(id.toString(), "alice", "loc-1", status, BigDecimal.TEN, List.of(),
            Instant.parse("2026-01-01T10:00:00Z"), updatedAt);
    }

    @Test
    void missingView_isCreatedFromTheSnapshot() {
        when(views.findById(id.toString())).thenReturn(Optional.empty());

        listener.apply(snapshot(Instant.parse("2026-01-01T11:00:00Z"), "COMPLETED"));

        ArgumentCaptor<OrderView> saved = ArgumentCaptor.forClass(OrderView.class);
        verify(views).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo("COMPLETED");
        assertThat(saved.getValue().createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));   // original timestamp kept
    }

    @Test
    void olderView_isOverwritten() {
        when(views.findById(id.toString())).thenReturn(Optional.of(existing(Instant.parse("2026-01-01T10:30:00Z"), "PAYMENT_PENDING")));

        listener.apply(snapshot(Instant.parse("2026-01-01T11:00:00Z"), "COMPLETED"));

        verify(views).save(any(OrderView.class));
    }

    @Test
    void newerLiveView_isNotOverwritten() {
        when(views.findById(id.toString())).thenReturn(Optional.of(existing(Instant.parse("2026-01-01T12:00:00Z"), "COMPLETED")));

        listener.apply(snapshot(Instant.parse("2026-01-01T11:00:00Z"), "PAYMENT_PENDING"));

        verify(views, never()).save(any(OrderView.class));
    }

    @Test
    void onlySnapshotEventsAreHandled() throws Exception {
        ConsumerRecord<String, String> r = new ConsumerRecord<>("restaurant.events.order", 0, 0L, "k", "{}");
        r.headers().add("eventType", "OrderCreated".getBytes(StandardCharsets.UTF_8));

        listener.onEvent(r, ack);

        verify(views, never()).save(any(OrderView.class));
        verify(ack).acknowledge();
    }
}
