package com.BoiteNoire.model.payloads;

public record ErrorPayload(
    String service,
    String message,
    String Severity,
    String errorType
) {
    
}
