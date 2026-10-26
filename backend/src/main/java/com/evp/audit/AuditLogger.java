package com.evp.audit;

import java.util.Map;

/**
 * Integration point with SEC-17 (append-only, hash-chained audit log, owned by the
 * Tally, audit and pipeline member).
 *
 * SEC-05 and SEC-11 call this for every security relevant step. Until the SEC-17
 * implementation exists, {@link Slf4jAuditLogger} writes to the application log. When
 * theirs is ready, delete Slf4jAuditLogger or mark their bean @Primary.
 *
 * Implementations that write to the database should use REQUIRES_NEW, so that a
 * refusal (for example a rejected self-approval) is still recorded when the calling
 * transaction rolls back.
 */
public interface AuditLogger {

    void record(String eventType, String actor, Map<String, String> details);
}
