package com.evp.audit;

import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Temporary AuditLogger until SEC-17's tamper-evident implementation replaces it. */
@Component
public class Slf4jAuditLogger implements AuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    @Override
    public void record(String eventType, String actor, Map<String, String> details) {
        AUDIT.info("event={} actor={} details={}", eventType, actor, new TreeMap<>(details));
    }
}
