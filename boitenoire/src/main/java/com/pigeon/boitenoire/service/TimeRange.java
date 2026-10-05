package com.pigeon.boitenoire.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

import org.springframework.data.mongodb.core.query.Criteria;

import com.pigeon.boitenoire.exception.InvalidRequestException;

public record TimeRange(Instant from, Instant toExclusive) {

    public static TimeRange of(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(
                    "Parameter 'from' (" + from + ") must not be after parameter 'to' (" + to + ")");
        }
        return new TimeRange(
                from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    public boolean isBounded() {
        return from != null || toExclusive != null;
    }

    public Criteria timestampCriteria() {
        Criteria criteria = Criteria.where("timestamp");
        if (from != null) {
            criteria = criteria.gte(Date.from(from));
        }
        if (toExclusive != null) {
            criteria = criteria.lt(Date.from(toExclusive));
        }
        return criteria;
    }
}
