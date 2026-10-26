package com.evp.identity.session;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Server-side record only: no access tokens, passwords or ballot identifiers. */
@Entity
@Table(name = "identity_bound_session")
public class BoundSession {
    @Id String id;
    @jakarta.persistence.Column(unique = true, nullable = false, length = 43)
    String cookieHash;
    String tokenHash;
    Instant expiresAt;
    Instant lastActivity;
    boolean revoked;
    Instant authenticatedAt;
    Boolean castVerified = false;
    @Version Long version;
    protected BoundSession() {}
    BoundSession(String id, String cookieHash, String tokenHash, Instant expiresAt, Instant now) {
        this.id = id; this.cookieHash = cookieHash; this.tokenHash = tokenHash;
        this.expiresAt = expiresAt; this.lastActivity = now;
    }
}
