package com.example.kitchen;

import com.example.kitchen.outbox.OutboxRepository;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    public record Line(String menuItemId, int quantity) {}

    private final JdbcTemplate jdbc;
    private final OutboxRepository outbox;

    public InventoryService(JdbcTemplate jdbc, OutboxRepository outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    /**
     * All-or-nothing reservation. Stock rows are locked in a stable (sorted) order to avoid deadlocks.
     * Reservation row + saga reply (outbox) commit in one transaction. Idempotent per order.
     */
    @Transactional
    public void reserve(UUID orderId, List<Line> lines) {
        List<String> existing = jdbc.query("SELECT status FROM reservations WHERE order_id = ?",
            (rs, i) -> rs.getString(1), orderId);
        if (!existing.isEmpty()) {                       // already decided: just repeat the answer
            if ("RESERVED".equals(existing.get(0))) reply(orderId, "InventoryReserved", null);
            else reply(orderId, "InventoryFailed", "Insufficient stock");
            return;
        }

        Map<String, Integer> need = new TreeMap<>();     // merge duplicate items, sorted by id
        for (Line l : lines) need.merge(l.menuItemId(), l.quantity(), Integer::sum);

        String shortage = null;
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            List<Integer> stock = jdbc.query("SELECT stock FROM inventory WHERE menu_item_id = ? FOR UPDATE",
                (rs, i) -> rs.getInt(1), e.getKey());
            if (stock.isEmpty() || stock.get(0) < e.getValue()) {
                shortage = e.getKey();
                break;
            }
        }

        if (shortage != null) {
            jdbc.update("INSERT INTO reservations(order_id, status) VALUES (?, 'FAILED')", orderId);
            reply(orderId, "InventoryFailed", "Insufficient stock for " + shortage);
            return;
        }

        for (Map.Entry<String, Integer> e : need.entrySet()) {
            jdbc.update("UPDATE inventory SET stock = stock - ? WHERE menu_item_id = ?", e.getValue(), e.getKey());
        }
        jdbc.update("INSERT INTO reservations(order_id, status) VALUES (?, 'RESERVED')", orderId);
        reply(orderId, "InventoryReserved", null);
    }

    private void reply(UUID orderId, String type, String reason) {
        Map<String, Object> payload = reason == null
            ? Map.of("orderId", orderId)
            : Map.of("orderId", orderId, "reason", reason);
        outbox.append("saga-reply", orderId.toString(), type, payload);
    }
}
