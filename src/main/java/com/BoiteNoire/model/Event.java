package com.BoiteNoire.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

//main model representing an event in our mangodb collection using record

@Document (collection = "events")
public record Event(
    @Id 
    String id,
    String type,
    String usedId,
    Instant timestamp,
    Object payload
) {
    
}