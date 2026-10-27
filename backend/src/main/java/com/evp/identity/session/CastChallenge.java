package com.evp.identity.session;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "identity_cast_challenge")
public class CastChallenge {
    @Id String id;
    String subjectHash;
    Instant createdAt;
    Instant expiresAt;
    Instant previousAuthentication;
    boolean used;
    @Version Long version;
    protected CastChallenge() {}
    CastChallenge(String id, String subjectHash, Instant now, Instant previousAuthentication) {
        this.id = id; this.subjectHash = subjectHash; this.createdAt = now;
        this.expiresAt = now.plusSeconds(180); this.previousAuthentication = previousAuthentication;
    }
}
