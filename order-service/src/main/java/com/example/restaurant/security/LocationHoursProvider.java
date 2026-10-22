package com.example.restaurant.security;

import java.time.Instant;

public interface LocationHoursProvider {
    boolean isOpen(String locationId, Instant at);
}
