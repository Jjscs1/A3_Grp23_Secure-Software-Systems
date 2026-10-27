package com.evp.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    @Bean
    AlgorithmRegistry ballotAlgorithmRegistry() {
        return AlgorithmRegistry.withDefaults();
    }

    @Bean
    KeyringHolder ballotKeyringHolder(@Value("${evp.ballot.keyring-dir:}") String dir,
                                      @Value("${evp.ballot.active-kid:}") String activeKid,
                                      @Value("${evp.ballot.ephemeral-keys:false}") boolean ephemeral) {
        return new KeyringHolder(dir, activeKid, ephemeral);
    }

    @Bean
    EnvelopeService envelopeService(AlgorithmRegistry registry, KeyringHolder holder) {
        return new EnvelopeService(registry, holder::get);
    }
}
