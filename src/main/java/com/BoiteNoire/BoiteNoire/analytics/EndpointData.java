package com.BoiteNoire.BoiteNoire.analytics;

public record EndpointData(
    String endpoint,
    double averageDuration,
    double p95Duration
) {

    
}
