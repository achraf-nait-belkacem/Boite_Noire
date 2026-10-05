package com.BoiteNoire.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

//main model representing an event in our mangodb collection using record
//map our class onto our collection

@Document (collection = "events")
public record Event(
    @Id //convert id into string
    String id,
    String type, // might better be an enum ?
    String usedrId,
    Instant timestamp, // java's type
    Object payload
) 
{

    public Event (String type, String userId, Instant timestamp, Object payload)    
    {
        this (null, type, userId, timestamp, payload);
    }
}