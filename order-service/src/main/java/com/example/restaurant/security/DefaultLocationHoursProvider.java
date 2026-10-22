package com.example.restaurant.security;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Placeholder: every location is open 08:00-23:00 UTC. Replace with a lookup (DB/cache). */
@Component
class DefaultLocationHoursProvider implements LocationHoursProvider {
    @Override
    public boolean isOpen(String locationId, Instant at) {
        int hour = at.atZone(ZoneOffset.UTC).getHour();
        return hour >= 8 && hour < 23;
    }
}
