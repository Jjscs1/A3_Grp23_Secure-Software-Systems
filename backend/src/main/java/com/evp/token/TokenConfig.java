package com.evp.token;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenConfig {

    private static final Logger log = LoggerFactory.getLogger(TokenConfig.class);

    /**
     * Fails closed: without EVP_TOKEN_KEY_PATH the app will not start, unless
     * evp.token.allow-ephemeral-key=true (tests and local development only).
     */
    @Bean
    public BlindSigner blindSigner(@Value("${evp.token.private-key-path:}") String keyPath,
                                   @Value("${evp.token.allow-ephemeral-key:false}") boolean allowEphemeral) {
        if (keyPath != null && !keyPath.isBlank()) {
            return new BlindSigner(PemKeys.loadRsaKeyPair(Path.of(keyPath)));
        }
        if (!allowEphemeral) {
            throw new IllegalStateException(
                    "No election signing key configured. Set EVP_TOKEN_KEY_PATH to a PKCS#8 PEM file.");
        }
        log.warn("Using an ephemeral election signing key. Tokens will not survive a restart. Development only.");
        return new BlindSigner(BlindSigner.generateKeyPair(2048));
    }
}
