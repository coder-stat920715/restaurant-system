package com.example.restaurant.admin;

import com.example.restaurant.query.OrderView;
import com.example.restaurant.query.ProcessedEvent;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rebuild procedure:
 *   1. DELETE /admin/read-model                (this service: empties the read model)
 *   2. POST   /admin/replay/orders             (order-service: re-publishes every order as OrderSnapshot)
 *   3. GET    /admin/read-model/stats          (compare orderViews with the "republished" count from step 2)
 */
@RestController
@RequestMapping("/admin/read-model")
class ReadModelAdminController {

    private final MongoTemplate mongo;

    ReadModelAdminController(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @DeleteMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Long> clear() {
        long views = mongo.remove(new Query(), OrderView.class).getDeletedCount();
        long markers = mongo.remove(new Query(), ProcessedEvent.class).getDeletedCount();
        return Map.of("orderViewsDeleted", views, "processedMarkersDeleted", markers);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Long> stats() {
        return Map.of("orderViews", mongo.count(new Query(), OrderView.class));
    }
}
