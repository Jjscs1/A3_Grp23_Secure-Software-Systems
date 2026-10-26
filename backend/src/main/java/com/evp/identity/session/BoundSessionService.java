package com.evp.identity.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class BoundSessionService {
    public static final String COOKIE = "__Host-evp-session";
    private final BoundSessionRepository records;
    private final Clock clock;
    private final NimbusJwtDecoder idTokens;

    public BoundSessionService(BoundSessionRepository records, Clock clock,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        this.records = records; this.clock = clock;
        // Deliberately not a JwtDecoder bean: resource-server access-token validation stays intact.
        this.idTokens = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256).build();
        this.idTokens.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("evp-frontend"))));
    }

    public synchronized String start(Jwt access, String idToken, String existingCookie) {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Jwt identity;
        try { identity = idTokens.decode(idToken); }
        catch (RuntimeException e) { throw new SessionRejected(); }
        Object value = identity.getClaims().get("auth_time");
        if (!(value instanceof Number time)) throw new SessionRejected();
        Instant authenticatedAt = Instant.ofEpochSecond(time.longValue());
        String sid = access.getClaimAsString("sid");
        if (sid == null || sid.isBlank() || !sid.equals(identity.getClaimAsString("sid"))
                || !access.getSubject().equals(identity.getSubject())
                || authenticatedAt.isAfter(now.plusSeconds(5))
                || authenticatedAt.isBefore(now.minusSeconds(60))
                || identity.getExpiresAt() == null || !identity.getExpiresAt().isAfter(now)
                || (identity.hasClaim("azp") && !"evp-frontend".equals(identity.getClaimAsString("azp")))
                || (identity.getAudience().size() > 1 && !"evp-frontend".equals(identity.getClaimAsString("azp")))) {
            throw new SessionRejected();
        }
        if (access.getIssuedAt() == null || access.getExpiresAt() == null
                || !access.getExpiresAt().isAfter(now)
                || Duration.between(access.getIssuedAt(), access.getExpiresAt()).getSeconds() > 600) {
            throw new SessionRejected();
        }
        // Reusing or refreshing the same authentication event cannot reopen a closed session.
        String id = hash(access.getIssuer() + "\n" + access.getSubject() + "\n" + sid + "\n" + time.longValue());
        BoundSession prior = records.findById(id).orElse(null);
        if (prior != null) {
            check(prior, access, existingCookie);
            return existingCookie;
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String cookie = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        records.saveAndFlush(new BoundSession(id, hash(cookie), hash(access.getTokenValue()), access.getExpiresAt(), now));
        return cookie;
    }

    public synchronized void require(Jwt access, String cookie) { lookup(access, cookie); }
    public synchronized void activity(Jwt access, String cookie) {
        BoundSession session = lookup(access, cookie);
        session.lastActivity = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        records.saveAndFlush(session);
    }
    public synchronized void end(Jwt access, String cookie) {
        BoundSession session = lookup(access, cookie);
        session.revoked = true;
        records.saveAndFlush(session);
    }
    private BoundSession lookup(Jwt access, String cookie) {
        if (cookie == null || cookie.isBlank()) throw new SessionRejected();
        BoundSession session = records.findByCookieHash(hash(cookie)).orElseThrow(SessionRejected::new);
        check(session, access, cookie);
        return session;
    }
    private void check(BoundSession session, Jwt access, String cookie) {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if (cookie == null || !session.cookieHash.equals(hash(cookie))
                || !session.tokenHash.equals(hash(access.getTokenValue())) || session.revoked
                || !now.isBefore(session.expiresAt)
                || !now.isBefore(session.lastActivity.plusSeconds(90))) throw new SessionRejected();
    }
    static String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
