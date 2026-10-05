package com.pigeon.boitenoire.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pigeon.boitenoire.dto.ErrorBreakdownResponse;
import com.pigeon.boitenoire.dto.FunnelResponse;
import com.pigeon.boitenoire.dto.LatencyResponse;
import com.pigeon.boitenoire.dto.TopUserResponse;
import com.pigeon.boitenoire.exception.ErrorResponse;
import com.pigeon.boitenoire.exception.InvalidRequestException;
import com.pigeon.boitenoire.service.AnalyticsService;
import com.pigeon.boitenoire.service.TimeRange;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Read-only analyses of the events, each computed by one MongoDB aggregation pipeline")
@ApiResponse(responseCode = "400", description = "Invalid or missing parameter, or 'from' after 'to'",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class AnalyticsController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "Most active users",
            description = "Users with the most events over the period, with their name and email. "
                    + "Pipeline: $match, $group, $sort, $limit, $lookup.")
    @GetMapping("/top-users")
    public List<TopUserResponse> topUsers(
            @Parameter(description = "First day of the period, inclusive (UTC, yyyy-MM-dd)", example = "2026-03-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day of the period, inclusive (UTC, yyyy-MM-dd)", example = "2026-03-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Number of users to return (1 to 100)", example = "10")
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("Parameter 'limit' must be between 1 and " + MAX_LIMIT);
        }
        return analyticsService.topUsers(TimeRange.of(from, to), limit);
    }

    @Operation(summary = "Errors by day, service and message",
            description = "Number of ERROR events grouped by day (UTC), service and message, "
                    + "sorted by day then by decreasing count. Pipeline: $match, $group ($dateToString), $sort.")
    @GetMapping("/errors")
    public List<ErrorBreakdownResponse> errors(
            @Parameter(description = "First day of the period, inclusive (UTC, yyyy-MM-dd)", example = "2026-03-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day of the period, inclusive (UTC, yyyy-MM-dd)", example = "2026-03-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.errors(TimeRange.of(from, to));
    }

    @Operation(summary = "Response time per endpoint",
            description = "Call count, average, 95th percentile and maximum duration of the API calls of each "
                    + "endpoint, slowest p95 first. Without a period, all API calls are analysed. "
                    + "Pipeline: $match, $group ($avg, $percentile), $project, $sort.")
    @GetMapping("/latency")
    public List<LatencyResponse> latency(
            @Parameter(description = "First day of the period, inclusive (UTC, yyyy-MM-dd); optional", example = "2026-03-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day of the period, inclusive (UTC, yyyy-MM-dd); optional", example = "2026-03-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.latency(TimeRange.of(from, to));
    }

    @Operation(summary = "Conversion funnel",
            description = "Users who, within the period, have a successful LOGIN, then an API_CALL POST /messages, "
                    + "then a PAYMENT with status SUCCESS, in that chronological order (first date of each step). "
                    + "Pipeline: $match, $group per user (conditional $min), $group counting each step.")
    @GetMapping("/funnel")
    public FunnelResponse funnel(
            @Parameter(description = "First day of the period, inclusive (UTC, yyyy-MM-dd)", example = "2026-03-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day of the period, inclusive (UTC, yyyy-MM-dd)", example = "2026-03-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.funnel(TimeRange.of(from, to));
    }
}
