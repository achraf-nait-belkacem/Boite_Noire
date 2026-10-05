package com.pigeon.boitenoire.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Conversion funnel: successful login, then POST /messages, then successful payment")
public record FunnelResponse(List<FunnelStepResponse> steps) {
}
