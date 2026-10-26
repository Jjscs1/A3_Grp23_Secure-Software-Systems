package com.evp.identity.session;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoundSessionRepository extends JpaRepository<BoundSession, String> {
    Optional<BoundSession> findByCookieHash(String cookieHash);
}
