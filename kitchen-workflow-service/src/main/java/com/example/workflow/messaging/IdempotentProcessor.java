package com.example.workflow.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class IdempotentProcessor {
    private final JdbcTemplate jdbc;
    IdempotentProcessor(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void once(String eventId, Runnable work) {
        int inserted = jdbc.update(
            "INSERT INTO processed_message(event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId);
        if (inserted == 1) work.run();
    }
}
