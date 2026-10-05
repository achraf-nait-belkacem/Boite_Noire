package com.pigeon.boitenoire.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user and the number of events they produced over the period")
public record TopUserResponse(
        @Schema(example = "658ae5913415ac4a9e6d83fe") String userId,
        @Schema(example = "Lucas Martin") String name,
        @Schema(example = "lucas.martin.42@example.com") String email,
        @Schema(example = "7942") long eventCount) {
}
