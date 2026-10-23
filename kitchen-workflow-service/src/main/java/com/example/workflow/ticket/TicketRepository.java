package com.example.workflow.ticket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TicketRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public TicketRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void saveSnapshot(UUID orderId, String customerId, String locationId, String linesJson) {
        jdbc.update("""
            INSERT INTO order_snapshot(order_id, customer_id, location_id, lines) VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT (order_id) DO UPDATE SET customer_id = EXCLUDED.customer_id,
                location_id = EXCLUDED.location_id, lines = EXCLUDED.lines
            """, orderId, customerId, locationId, linesJson);
    }

    /** Creates the QUEUED ticket from the stored snapshot. Returns false if there is no snapshot or a ticket already exists. */
    public boolean createFromSnapshot(UUID orderId) {
        return jdbc.update("""
            INSERT INTO kitchen_ticket(id, order_id, location_id, status, items)
            SELECT gen_random_uuid(), order_id, location_id, 'QUEUED', lines FROM order_snapshot WHERE order_id = ?
            ON CONFLICT (order_id) DO NOTHING
            """, orderId) == 1;
    }

    public Optional<KitchenTicket> findById(UUID id) {
        return jdbc.query("SELECT * FROM kitchen_ticket WHERE id = ?", this::map, id).stream().findFirst();
    }

    public List<KitchenTicket> listByLocation(String locationId, TicketStatus status) {
        if (status == null) {
            return jdbc.query("SELECT * FROM kitchen_ticket WHERE location_id = ? ORDER BY created_at",
                this::map, locationId);
        }
        return jdbc.query("SELECT * FROM kitchen_ticket WHERE location_id = ? AND status = ? ORDER BY created_at",
            this::map, locationId, status.name());
    }

    /** Compare-and-set on the expected status: two chefs cannot both move the same ticket. */
    public boolean transition(UUID id, TicketStatus from, TicketStatus to, String user) {
        return jdbc.update("""
            UPDATE kitchen_ticket SET status = ?, updated_at = now(), updated_by = ? WHERE id = ? AND status = ?
            """, to.name(), user, id, from.name()) == 1;
    }

    private KitchenTicket map(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime updated = rs.getObject("updated_at", OffsetDateTime.class);
        return new KitchenTicket(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getString("location_id"),
            TicketStatus.valueOf(rs.getString("status")),
            readJson(rs.getString("items")),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            updated == null ? null : updated.toInstant(),
            rs.getString("updated_by"));
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
