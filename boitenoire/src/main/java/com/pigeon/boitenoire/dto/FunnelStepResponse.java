package com.pigeon.boitenoire.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One step of the conversion funnel")
public record FunnelStepResponse(
        @Schema(example = "POST_MESSAGE") String step,
        @Schema(description = "Users who reached this step after completing the previous ones", example = "1200") long users,
        @Schema(description = "Percentage of the previous step's users who reached this step", example = "60.0") double conversionFromPreviousPct,
        @Schema(description = "Percentage of the first step's users who reached this step", example = "60.0") double conversionFromFirstPct) {
}
