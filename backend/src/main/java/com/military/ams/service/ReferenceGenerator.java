package com.military.ams.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * Builds human readable, sortable document references such as PUR-2026-0007.
 * Uniqueness is still guaranteed by the database unique index, so a collision
 * under concurrency fails the transaction rather than creating a duplicate.
 */
@Component
public class ReferenceGenerator {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

    /** e.g. seriesPrefix("PUR") -&gt; "PUR-2026-" */
    public String seriesPrefix(String documentPrefix) {
        return documentPrefix + "-" + YEAR.format(LocalDate.now()) + "-";
    }

    public String next(String seriesPrefix, long existingInSeries) {
        return seriesPrefix + String.format("%04d", existingInSeries + 1);
    }
}
