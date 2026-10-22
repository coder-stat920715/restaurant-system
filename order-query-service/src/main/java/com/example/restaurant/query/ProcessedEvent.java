package com.example.restaurant.query;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("processed_event")
public record ProcessedEvent(@Id String eventId, Instant at) {}
