package com.BoiteNoire.model.payloads;

public record ApiCallPayload(
    String endpoint,
    String method,
    int durationMs,
    int statusCode
) {
    
}
