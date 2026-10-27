package com.evp.secrecy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Cast time is stored to the hour only (UTC), e.g. 2026-10-27T11:00Z, so it cannot be matched to a login or token request. */
public final class HourCoarsener {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00'Z'").withZone(ZoneOffset.UTC);

    private HourCoarsener() { }

    public static String toHour(Instant t) {
        return FMT.format(t.truncatedTo(ChronoUnit.HOURS));
    }
}
