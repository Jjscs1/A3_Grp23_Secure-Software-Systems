package com.evp.identity.access;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import com.evp.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;

class AdminElevationTests {
    private static final long NOW = 1800000000L;
    private final AdminElevation policy = new AdminElevation(Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    private Jwt token(Object start, Object end) {
        var builder = Jwt.withTokenValue("unit-test").header("alg", "RS256").subject("admin")
            .claim("realm_access", Map.of("roles", List.of("ADMIN", "AUDITOR", "unknown")));
        if (start != null) builder.claim(AdminElevation.START, start);
        if (end != null) builder.claim(AdminElevation.END, end);
        return builder.build();
    }
    @Test void acceptsWithinFourHourWindow() {
        assertThat(policy.isActive(token(NOW - 14399, NOW + 1))).isTrue();
    }
    @Test void rejectsAtExactlyFourHours() {
        assertThat(policy.isActive(token(NOW - 14400, NOW))).isFalse();
    }
    @Test void rejectsGrantLongerThanFourHoursEvenIfCurrentlyFresh() {
        assertThat(policy.isActive(token(NOW, NOW + 14401))).isFalse();
    }
    @Test void rejectsMissingClaims() {
        assertThat(policy.isActive(token(null, NOW + 60))).isFalse();
        assertThat(policy.isActive(token(NOW, null))).isFalse();
    }
    @Test void rejectsFutureReversedAndOverflowValues() {
        assertThat(policy.isActive(token(NOW + 1, NOW + 60))).isFalse();
        assertThat(policy.isActive(token(NOW, NOW - 1))).isFalse();
        assertThat(policy.isActive(token(Long.MIN_VALUE, Long.MAX_VALUE))).isFalse();
    }
    @Test void rejectsMalformedClaims() {
        for (Object value : List.of("1800000000", 1800000000.5, true, List.of(NOW))) {
            assertThat(policy.isActive(token(value, NOW + 60))).isFalse();
            assertThat(policy.isActive(token(NOW, value))).isFalse();
        }
    }
    @Test void expiredAdminLosesOnlyAdminAndUnknownRolesAreNeverGranted() {
        var auth = SecurityConfig.keycloakRoles(policy).convert(token(NOW - 14401, NOW - 1));
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_AUDITOR");
    }
    @Test void freshGrantDoesNotGiveAdminToUserWithoutAdminRole() {
        Jwt voter = Jwt.withTokenValue("test").header("alg", "RS256").subject("voter")
            .claim("realm_access", Map.of("roles", List.of("VOTER")))
            .claim(AdminElevation.START, NOW).claim(AdminElevation.END, NOW + 60).build();
        assertThat(SecurityConfig.keycloakRoles(policy).convert(voter).getAuthorities())
            .extracting("authority").containsExactly("ROLE_VOTER");
    }
    @Test void expiryIsRecheckedAgainstCurrentTimeForSameToken() {
        Jwt jwt = token(NOW, NOW + 60);
        assertThat(policy.isActive(jwt)).isTrue();
        var later = new AdminElevation(Clock.fixed(Instant.ofEpochSecond(NOW + 60), ZoneOffset.UTC));
        assertThat(later.isActive(jwt)).isFalse();
    }
}
