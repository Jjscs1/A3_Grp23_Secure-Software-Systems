package com.evp.results;

import com.evp.security.Ed25519Keys;
import java.nio.file.Path;
import java.security.KeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SEC-18: the results signing key, separate from the audit key (SEC-17) and the token key
 * (SEC-11), so one compromised key does not compromise the others.
 *
 * Fails closed: without EVP_RESULTS_KEY_PATH and EVP_RESULTS_PUBLIC_KEY_PATH the app will
 * not start, unless evp.results.allow-ephemeral-key=true (tests only).
 */
@Configuration
public class ResultsSigningConfig {

    private static final Logger log = LoggerFactory.getLogger(ResultsSigningConfig.class);

    public record ResultsSigningKey(KeyPair keyPair) {
    }

    @Bean
    public ResultsSigningKey resultsSigningKey(@Value("${evp.results.private-key-path:}") String privatePath,
                                               @Value("${evp.results.public-key-path:}") String publicPath,
                                               @Value("${evp.results.allow-ephemeral-key:false}") boolean allowEphemeral) {
        if (!privatePath.isBlank() && !publicPath.isBlank()) {
            return new ResultsSigningKey(Ed25519Keys.load(Path.of(privatePath), Path.of(publicPath)));
        }
        if (!allowEphemeral) {
            throw new IllegalStateException("No results signing key configured. Set EVP_RESULTS_KEY_PATH and "
                    + "EVP_RESULTS_PUBLIC_KEY_PATH to Ed25519 PEM files (see Ed25519Keys).");
        }
        log.warn("Using an ephemeral results signing key. Published results will not verify after a restart. Tests only.");
        return new ResultsSigningKey(Ed25519Keys.generate());
    }
}