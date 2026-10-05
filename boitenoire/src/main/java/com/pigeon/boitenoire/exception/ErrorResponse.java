package com.pigeon.boitenoire.exception;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error returned by the API")
public record ErrorResponse(
        @Schema(example = "2026-10-05T14:30:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "Parameter 'from' must be after 'to'") String message) {
}
