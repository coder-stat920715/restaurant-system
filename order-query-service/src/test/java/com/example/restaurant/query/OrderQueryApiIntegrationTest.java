package com.example.restaurant.query;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Needs Docker (MongoDB). Kafka listeners are switched off, we only exercise the query API and its security. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderQueryApiIntegrationTest {

    @Container @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @MockBean JwtDecoder jwtDecoder;

    @Autowired MockMvc mvc;
    @Autowired OrderViewRepository views;

    @BeforeEach
    void seed() {
        views.deleteAll();
        Instant now = Instant.now();
        views.saveAll(List.of(
            new OrderView("o-alice-1", "alice", "loc-1", "COMPLETED", BigDecimal.TEN, List.of(), now, now),
            new OrderView("o-alice-2", "alice", "loc-1", "CANCELLED", BigDecimal.TEN, List.of(), now, now),
            new OrderView("o-bob-1", "bob", "loc-2", "COMPLETED", BigDecimal.TEN, List.of(), now, now)));
    }

    private JwtRequestPostProcessor user(String sub, String location, String role) {
        return jwt().jwt(j -> {
            j.subject(sub);
            if (location != null) j.claim("restaurantLocationId", location);
        }).authorities(new SimpleGrantedAuthority(role));
    }

    @Test
    void unauthenticated_isRejected() throws Exception {
        mvc.perform(get("/query/orders/o-alice-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerSeesOnlyOwnOrdersInTheirList() throws Exception {
        mvc.perform(get("/query/orders/mine").with(user("alice", null, "ROLE_CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void customerCannotReadSomeoneElsesOrder() throws Exception {
        mvc.perform(get("/query/orders/o-bob-1").with(user("alice", null, "ROLE_CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/query/orders/o-alice-1").with(user("alice", null, "ROLE_CUSTOMER")))
            .andExpect(status().isOk());
    }

    @Test
    void chefReadsOnlyOrdersOfTheirOwnLocation() throws Exception {
        mvc.perform(get("/query/orders/o-alice-1").with(user("chef1", "loc-1", "ROLE_CHEF")))
            .andExpect(status().isOk());
        mvc.perform(get("/query/orders/o-bob-1").with(user("chef1", "loc-1", "ROLE_CHEF")))
            .andExpect(status().isForbidden());
    }

    @Test
    void kitchenBoardIsScopedToLocationAndRole() throws Exception {
        mvc.perform(get("/query/orders/kitchen-board").with(user("chef1", "loc-1", "ROLE_CHEF")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].orderId").value("o-alice-1"));
        mvc.perform(get("/query/orders/kitchen-board").with(user("alice", null, "ROLE_CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyOrder() throws Exception {
        mvc.perform(get("/query/orders/o-bob-1").with(user("root", null, "ROLE_SUPER_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void onlySuperAdminCanClearTheReadModel() throws Exception {
        mvc.perform(delete("/admin/read-model").with(user("chef1", "loc-1", "ROLE_CHEF")))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/admin/read-model").with(user("root", null, "ROLE_SUPER_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderViewsDeleted").value(3));

        mvc.perform(get("/admin/read-model/stats").with(user("root", null, "ROLE_SUPER_ADMIN")))
            .andExpect(jsonPath("$.orderViews").value(0));
    }
}
