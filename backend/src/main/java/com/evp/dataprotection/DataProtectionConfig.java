package com.evp.dataprotection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class DataProtectionConfig {

    /** Keys come from environment variables only; start-up fails if they are missing or weak. */
    @Bean
    public DataKeys dataKeys(@Value("${evp.data.allow-ephemeral-keys:false}") boolean allowEphemeral) {
        DataKeys k = DataKeys.fromEnvOrEphemeral(System.getenv(), allowEphemeral);
        EncryptedStringConverter.init(new FieldCipher(k));
        return k;
    }

    @Bean
    public FieldCipher fieldCipher(DataKeys keys) {
        return new FieldCipher(keys);
    }

    @Bean
    public BlindIndex blindIndex(DataKeys keys) {
        return new BlindIndex(keys);
    }

    @Bean
    public FilterRegistrationBean<HstsFilter> hstsFilter(@Value("${evp.tls.canonical-host:localhost:8443}") String host,
                                                         @Value("${evp.tls.trust-forwarded-proto:false}") boolean trustProto) {
        FilterRegistrationBean<HstsFilter> b = new FilterRegistrationBean<>(new HstsFilter(host, trustProto));
        b.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        b.addUrlPatterns("/*");
        return b;
    }
}
