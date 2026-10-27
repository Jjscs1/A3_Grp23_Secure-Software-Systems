package com.evp.audit;
import com.evp.security.Ed25519Keys;
import java.nio.file.Path;
import java.security.KeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditSigningConfig {

    private static final Logger log = LoggerFactory.getLogger(AuditSigningConfig.class);

    public record AuditSigningKey(KeyPair keyPair) {
    }

    @Bean
    public AuditSigningKey auditSigningKey(@Value("${evp.audit.private-key-path:}") String privatePath,
                                           @Value("${evp.audit.public-key-path:}") String publicPath,
                                           @Value("${evp.audit.allow-ephemeral-key:false}") boolean allowEphemeral) {
        if (!privatePath.isBlank() && !publicPath.isBlank()) {
            return new AuditSigningKey(Ed25519Keys.load(Path.of(privatePath), Path.of(publicPath)));
        }
        if (!allowEphemeral) {
            throw new IllegalStateException("No audit signing key configured. Set EVP_AUDIT_KEY_PATH and "
                    + "EVP_AUDIT_PUBLIC_KEY_PATH to Ed25519 PEM files (see Ed25519Keys).");
        }
        log.warn("Using an ephemeral audit signing key. Old entries will not verify after a restart. Tests only.");
        return new AuditSigningKey(Ed25519Keys.generate());
    }
}