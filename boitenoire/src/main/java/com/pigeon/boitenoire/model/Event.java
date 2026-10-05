package com.pigeon.boitenoire.model;

import java.time.Instant;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "events")
public record Event(
        @Id ObjectId id,
        EventType type,
        ObjectId userId,
        Instant timestamp,
        Map<String, Object> payload) {
}
