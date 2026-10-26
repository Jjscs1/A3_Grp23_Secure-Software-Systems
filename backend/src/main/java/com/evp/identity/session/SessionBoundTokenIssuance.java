package com.evp.identity.session;

import com.evp.token.TokenIssuanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Close identity access and issue the blind signature in the SAME transaction. */
@Service
public class SessionBoundTokenIssuance {
    private final BoundSessionService sessions;
    private final TokenIssuanceService issuance;
    private final boolean enabled;
    public SessionBoundTokenIssuance(BoundSessionService sessions, TokenIssuanceService issuance,
            @Value("${evp.session.enabled:true}") boolean enabled) {
        this.sessions = sessions; this.issuance = issuance; this.enabled = enabled;
    }
    @Transactional
    public byte[] issue(Jwt jwt, String cookie, String username, String division, byte[] blinded) {
        if (enabled) sessions.consumeCastSession(jwt, cookie);
        // If eligibility or signing fails, the session revocation rolls back as well.
        return issuance.issue(username, division, blinded);
    }
}
