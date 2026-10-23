package com.example.payment.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
class OutboxCleaner {
    private final JdbcTemplate jdbc;
    OutboxCleaner(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelay = 600_000)
    void purge() {
        jdbc.update("DELETE FROM outbox_event WHERE created_at < now() - interval '1 hour'");
    }
}
