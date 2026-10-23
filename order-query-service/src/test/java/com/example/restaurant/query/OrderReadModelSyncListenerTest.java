package com.example.restaurant.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.support.Acknowledgment;

class OrderReadModelSyncListenerTest {

    private static final String ORDER_ID = "11111111-1111-1111-1111-111111111111";

    private final OrderViewRepository views = mock(OrderViewRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OrderReadModelSyncListener listener = new OrderReadModelSyncListener(views, mongo, mapper);

    private ConsumerRecord<String, String> record(String type, String eventId, String json) {
        ConsumerRecord<String, String> r = new ConsumerRecord<>("restaurant.events.order", 0, 0L, ORDER_ID, json);
        r.headers().add("eventType", type.getBytes(StandardCharsets.UTF_8));
        r.headers().add("eventId", eventId.getBytes(StandardCharsets.UTF_8));
        return r;
    }

    private static final String CREATED = """
        {"orderId":"%s","customerId":"alice","locationId":"loc-1",
         "lines":[{"menuItemId":"burger","quantity":2,"unitPrice":9.50}],"total":19.00}
        """.formatted(ORDER_ID);

    @Test
    void orderCreated_projectsANewView() throws Exception {
        listener.onEvent(record("OrderCreated", "e1", CREATED), ack);

        ArgumentCaptor<OrderView> saved = ArgumentCaptor.forClass(OrderView.class);
        verify(views).save(saved.capture());
        OrderView v = saved.getValue();
        assertThat(v.orderId()).isEqualTo(ORDER_ID);
        assertThat(v.customerId()).isEqualTo("alice");
        assertThat(v.locationId()).isEqualTo("loc-1");
        assertThat(v.status()).isEqualTo("ORDER_CREATED");
        assertThat(v.lines()).hasSize(1);
        verify(ack).acknowledge();
    }

    @Test
    void duplicateEvent_isSkippedButAcknowledged() throws Exception {
        when(mongo.insert(any(ProcessedEvent.class))).thenThrow(new DuplicateKeyException("already processed"));

        listener.onEvent(record("OrderCreated", "e1", CREATED), ack);

        verify(views, never()).save(any(OrderView.class));
        verify(ack).acknowledge();
    }

    @Test
    void statusChanged_updatesTheStatusField() throws Exception {
        String json = "{\"orderId\":\"" + ORDER_ID + "\",\"status\":\"COMPLETED\",\"reason\":null}";

        listener.onEvent(record("OrderStatusChanged", "e2", json), ack);

        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(OrderView.class));
        verify(ack).acknowledge();
    }

    @Test
    void unknownEventTypes_areIgnored() throws Exception {
        listener.onEvent(record("SomethingNew", "e3", "{}"), ack);

        verify(views, never()).save(any(OrderView.class));
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(OrderView.class));
        verify(ack).acknowledge();
    }

    @Test
    void failure_removesTheDedupMarkerAndDoesNotAcknowledge() {
        assertThatThrownBy(() -> listener.onEvent(record("OrderCreated", "e4", "not-json"), ack))
            .isInstanceOf(IllegalStateException.class);

        verify(mongo).remove(any(Query.class), eq(ProcessedEvent.class));   // retry must be able to re-apply
        verify(ack, never()).acknowledge();
    }
}
