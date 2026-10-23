package com.example.workflow.security;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Placeholder: open 08:00-23:00 UTC everywhere. Replace with a real lookup. */
@Component
class DefaultLocationHoursProvider implements LocationHoursProvider {
    @Override
    public boolean isOpen(String locationId, Instant at) {
        int hour = at.atZone(ZoneOffset.UTC).getHour();
        return hour >= 8 && hour < 23;
    }
}
