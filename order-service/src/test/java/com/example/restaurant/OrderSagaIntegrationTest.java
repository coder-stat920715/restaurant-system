package com.example.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.restaurant.order.OrderRepository;
import com.example.restaurant.order.OrderStatus;
import com.example.restaurant.saga.SagaRepository;
import com.example.restaurant.saga.SagaState;
import com.example.restaurant.support.SagaTestSupport;
import com.example.restaurant.support.SagaTestSupport.OutboxRelay;
import com.example.restaurant.support.SagaTestSupport.Participants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Needs Docker. Skipped automatically when Docker is unavailable. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(SagaTestSupport.class)
class OrderSagaIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @MockBean JwtDecoder jwtDecoder;      // no real IdP in tests

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired OrderRepository orders;
    @Autowired SagaRepository sagas;
    @Autowired Participants participants;
    @Autowired OutboxRelay relay;

    @AfterEach
    void reset() { participants.reset(); }

    // ---------- helpers
    private JwtRequestPostProcessor user(String sub, String location, String role) {
        return jwt().jwt(j -> {
            j.subject(sub);
            if (location != null) j.claim("restaurantLocationId", location);
        }).authorities(new SimpleGrantedAuthority(role));
    }

    private UUID placeOrder(String customer, String unitPrice) throws Exception {
        String body = """
            {"locationId":"loc-1","lines":[{"menuItemId":"burger","quantity":1,"unitPrice":%s}]}
            """.formatted(unitPrice);
        String json = mvc.perform(post("/orders").contentType("application/json").content(body)
                        .with(user(customer, null, "ROLE_CUSTOMER")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(json).get("orderId").asText());
    }

    private void awaitStatus(UUID id, OrderStatus expected) {
        await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(expected));
    }

    // ---------- saga
    @Test
    void happyPath_completesAndPublishesEveryStateChange() throws Exception {
        UUID id = placeOrder("alice", "9.50");

        awaitStatus(id, OrderStatus.COMPLETED);
        assertThat(sagas.findById(id).orElseThrow().getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(relay.eventTypesFor(id.toString()))
                .contains("OrderCreated", "OrderStatusChanged", "ProcessPayment", "ReserveInventory");
    }

    @Test
    void paymentFailure_cancelsOrderWithoutReversal() throws Exception {
        UUID id = placeOrder("alice", "600.00");           // above the fake payment limit

        awaitStatus(id, OrderStatus.CANCELLED);
        assertThat(sagas.findById(id).orElseThrow().getState()).isEqualTo(SagaState.CANCELLED);
        assertThat(participants.reversedPayments()).doesNotContain(id.toString());
    }

    @Test
    void inventoryFailure_reversesPaymentThenCancels() throws Exception {
        participants.kitchenFails(true);
        UUID id = placeOrder("alice", "9.50");

        awaitStatus(id, OrderStatus.CANCELLED);
        assertThat(participants.reversedPayments()).contains(id.toString());
        assertThat(sagas.findById(id).orElseThrow().getFailureReason()).isEqualTo("Out of stock");
    }

    @Test
    void lateOutOfOrderReply_isIgnored() throws Exception {
        UUID id = placeOrder("alice", "9.50");
        awaitStatus(id, OrderStatus.COMPLETED);

        participants.sendReply(id.toString(), "PaymentFailed", UUID.randomUUID().toString(),
                Map.of("orderId", id.toString(), "reason", "late"));

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED));
    }

    // ---------- security through the real filter chain + method security
    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(post("/orders").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orderReadsAreScopedByOwnerAndLocation() throws Exception {
        UUID id = placeOrder("alice", "9.50");
        String url = "/orders/" + id;

        mvc.perform(get(url).with(user("alice", null, "ROLE_CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(get(url).with(user("bob", null, "ROLE_CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(user("chef1", "loc-1", "ROLE_CHEF"))).andExpect(status().isOk());
        mvc.perform(get(url).with(user("chef2", "loc-2", "ROLE_CHEF"))).andExpect(status().isForbidden());
    }

    @Test
    void validationRejectsEmptyOrders() throws Exception {
        mvc.perform(post("/orders").contentType("application/json")
                        .content("{\"locationId\":\"loc-1\",\"lines\":[]}")
                        .with(user("alice", null, "ROLE_CUSTOMER")))
                .andExpect(status().isBadRequest());
    }
}
