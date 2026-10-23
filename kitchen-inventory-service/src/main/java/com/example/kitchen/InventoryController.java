package com.example.kitchen;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of stock, handy for demos. Internal network only. */
@RestController
class InventoryController {
    private final JdbcTemplate jdbc;
    InventoryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/inventory")
    List<Map<String, Object>> stock() {
        return jdbc.queryForList("SELECT menu_item_id, stock FROM inventory ORDER BY menu_item_id");
    }
}
