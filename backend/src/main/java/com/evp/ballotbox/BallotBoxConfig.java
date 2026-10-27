package com.evp.ballotbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class BallotBoxConfig {

    /**
     * Signing key: PKCS#8 private + X.509 public PEM in evp.ballotbox.key-dir (git-ignored, never committed).
     * If the directory is not configured the box refuses to start with a generated key unless evp.ballotbox.ephemeral=true.
     */
    @Bean
    public CheckpointSigner checkpointSigner(@Value("${evp.ballotbox.key-dir:}") String dir,
                                             @Value("${evp.ballotbox.kid:bb-1}") String kid,
                                             @Value("${evp.ballotbox.ephemeral:false}") boolean ephemeral) throws Exception {
        if (dir.isBlank()) {
            if (!ephemeral) {
                throw new IllegalStateException("evp.ballotbox.key-dir is not set (use evp.ballotbox.ephemeral=true for demos only)");
            }
            return CheckpointSigner.generate(kid);
        }
        return CheckpointSigner.fromPem(kid, Files.readString(Path.of(dir, kid + ".key.pem")), Files.readString(Path.of(dir, kid + ".pub.pem")));
    }

    @Bean
    public LogStore logStore(DataSource dataSource) {
        return new JdbcLogStore(dataSource);
    }

    @Bean
    public BallotBox ballotBox(LogStore store, CheckpointSigner signer) {
        return new BallotBox(store, signer, Clock.systemUTC());
    }
}
