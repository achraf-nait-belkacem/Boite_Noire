package com.pigeon.boitenoire.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Number of ERROR events for one day, service and message")
public record ErrorBreakdownResponse(
        @Schema(description = "Day in UTC", example = "2026-03-14") String day,
        @Schema(example = "messaging") String service,
        @Schema(example = "Connection refused") String message,
        @Schema(example = "12") long count) {
}
