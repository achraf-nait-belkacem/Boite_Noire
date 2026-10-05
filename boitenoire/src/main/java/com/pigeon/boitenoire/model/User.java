package com.pigeon.boitenoire.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public record User(
        @Id ObjectId id,
        String name,
        String email,
        Instant createdAt) {
}
