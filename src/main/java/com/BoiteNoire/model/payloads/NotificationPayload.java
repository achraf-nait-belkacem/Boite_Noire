package com.BoiteNoire.model.payloads;

public record NotificationPayload(
    String channel,
    String title,
    boolean read
) {
    
}
