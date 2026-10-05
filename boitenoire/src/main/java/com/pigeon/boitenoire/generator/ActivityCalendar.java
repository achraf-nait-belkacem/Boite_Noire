package com.pigeon.boitenoire.generator;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Random;

final class ActivityCalendar {

    static final int DAYS = 365;
    private static final int SLOTS = DAYS * 24;

    private static final double[] HOUR_WEIGHTS = {
            0.03, 0.02, 0.02, 0.02, 0.03, 0.06, 0.15, 0.35,
            0.65, 1.00, 1.00, 0.95, 0.50, 0.55, 0.90, 1.00,
            0.95, 0.85, 0.45, 0.25, 0.18, 0.12, 0.07, 0.04 };

    private final Instant start;
    private final Instant end;
    private final double[] cumulative = new double[SLOTS + 1];

    ActivityCalendar(LocalDate endExclusive, Random random) {
        LocalDate startDate = endExclusive.minusDays(DAYS);
        this.start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        this.end = endExclusive.atStartOfDay(ZoneOffset.UTC).toInstant();

        double[] dayWeights = new double[DAYS];
        for (int d = 0; d < DAYS; d++) {
            LocalDate date = startDate.plusDays(d);
            double weight = dayOfWeekWeight(date.getDayOfWeek());
            weight *= 1 + 0.15 * Math.sin(2 * Math.PI * date.getDayOfYear() / 365.0);
            weight *= Math.max(0.5, 1 + 0.15 * random.nextGaussian());
            dayWeights[d] = weight;
        }
        for (int i = 0; i < 8; i++) {
            dayWeights[random.nextInt(DAYS)] *= 2.0 + random.nextDouble();
        }
        for (int i = 0; i < 8; i++) {
            dayWeights[random.nextInt(DAYS)] *= 0.15 + 0.2 * random.nextDouble();
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            cumulative[slot + 1] = cumulative[slot] + dayWeights[slot / 24] * HOUR_WEIGHTS[slot % 24];
        }
    }

    Instant start() {
        return start;
    }

    Instant end() {
        return end;
    }

    Instant sample(Instant from, Instant to, Random random) {
        Instant lower = from.isBefore(start) ? start : from;
        Instant upper = to.isAfter(end) ? end : to;
        if (!lower.isBefore(upper)) {
            return lower.isBefore(end) ? lower : end.minusSeconds(1);
        }
        int firstSlot = (int) Duration.between(start, lower).toHours();
        int lastSlot = (int) Math.min(SLOTS, (Duration.between(start, upper).toSeconds() + 3599) / 3600);

        double low = cumulative[firstSlot];
        double target = low + random.nextDouble() * (cumulative[lastSlot] - low);
        int slot = firstSlot;
        int hi = lastSlot;
        while (hi - slot > 1) {
            int mid = (slot + hi) >>> 1;
            if (cumulative[mid] <= target) {
                slot = mid;
            } else {
                hi = mid;
            }
        }

        Instant result = start.plusSeconds(slot * 3600L + random.nextInt(3600));
        if (result.isBefore(lower)) {
            long remaining = Math.max(1, Duration.between(lower, start.plusSeconds((slot + 1) * 3600L)).toSeconds());
            result = lower.plusSeconds(random.nextInt((int) remaining));
        }
        if (!result.isBefore(upper)) {
            result = upper.minusSeconds(1);
        }
        return result;
    }

    private static double dayOfWeekWeight(DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> 0.40;
            case SUNDAY -> 0.30;
            case FRIDAY -> 0.90;
            default -> 1.00;
        };
    }
}
