package com.evp.enrolment;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnrolmentConfig {

    private static final Logger log = LoggerFactory.getLogger(EnrolmentConfig.class);

    /**
     * The HMAC secret comes from the EVP_LOOKUP_SECRET environment variable (base64, at
     * least 32 bytes), generated with: openssl rand -base64 32
     *
     * Fails closed: the app will not start without it, unless
     * evp.enrolment.allow-ephemeral-secret=true (tests only). No secret is ever committed.
     */
    @Bean
    public EnrolmentLookupKey enrolmentLookupKey(@Value("${evp.enrolment.lookup-secret:}") String base64Secret,
                                                 @Value("${evp.enrolment.allow-ephemeral-secret:false}") boolean allowEphemeral) {
        if (base64Secret != null && !base64Secret.isBlank()) {
            return new EnrolmentLookupKey(Base64.getDecoder().decode(base64Secret.trim()));
        }
        if (!allowEphemeral) {
            throw new IllegalStateException("No enrolment lookup secret configured. Set EVP_LOOKUP_SECRET.");
        }
        log.warn("Using a random enrolment lookup secret. Stored lookup keys will not match after a restart. Tests only.");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return new EnrolmentLookupKey(random);
    }

    @Bean
    public ResponseTimePadder enrolmentCheckPadder(@Value("${evp.enrolment.min-response-ms:300}") long minimumMillis) {
        return new ResponseTimePadder(Duration.ofMillis(minimumMillis));
    }
}
