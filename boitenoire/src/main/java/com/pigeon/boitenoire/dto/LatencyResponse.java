package com.pigeon.boitenoire.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response time statistics of one API endpoint")
public record LatencyResponse(
        @Schema(example = "/messages") String endpoint,
        @Schema(example = "POST") String method,
        @Schema(description = "Number of calls", example = "10208") long count,
        @Schema(description = "Average duration in milliseconds", example = "300.4") double avgMs,
        @Schema(description = "95th percentile of the duration in milliseconds (approximate)", example = "532.0") double p95Ms,
        @Schema(description = "Slowest call in milliseconds", example = "29926") long maxMs) {
}
