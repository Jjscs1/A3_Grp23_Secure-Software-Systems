package com.evp.identity.access;

import java.time.Clock;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Trusted, signed grant timestamps. Token refresh and login never start a new grant. */
@Component
public class AdminElevation {
    public static final String START = "admin_elevated_at";
    public static final String END = "admin_elevation_expires_at";
    public static final long MAX_SECONDS = 4 * 60 * 60;
    private final Clock clock;

    public AdminElevation(Clock clock) { this.clock = clock; }

    public boolean isActive(Jwt jwt) {
        Long start = integer(jwt.getClaims().get(START));
        Long end = integer(jwt.getClaims().get(END));
        long now = clock.instant().getEpochSecond();
        return start != null && end != null && start > 0 && start <= now
            && end > now && end > start && end - start <= MAX_SECONDS;
    }

    private static Long integer(Object value) {
        // Do not coerce strings, booleans, decimals, arrays or malformed claims into a grant.
        if (value instanceof Long number) return number;
        if (value instanceof Integer number) return number.longValue();
        return null;
    }
}
