package com.example.restaurant.replay;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal operations endpoint (not routed through the public gateway). */
@RestController
@RequestMapping("/admin/replay")
class AdminReplayController {

    private final OrderReplayService replay;

    AdminReplayController(OrderReplayService replay) {
        this.replay = replay;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Integer> replayOrders(@RequestParam(defaultValue = "500") int batchSize) {
        int size = Math.max(1, Math.min(batchSize, 5000));
        return Map.of("republished", replay.replayAll(size));
    }
}
