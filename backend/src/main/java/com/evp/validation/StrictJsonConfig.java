package com.evp.validation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies {@link StrictJson} to Spring's ObjectMapper. Switch off with evp.validation.strict-json=false
 * if a legacy client legitimately sends extra fields.
 */
@Configuration
public class StrictJsonConfig {

    @Bean
    @ConditionalOnProperty(name = "evp.validation.strict-json", havingValue = "true", matchIfMissing = true)
    Jackson2ObjectMapperBuilderCustomizer strictJsonCustomizer() {
        return builder -> builder.postConfigurer(StrictJson::apply);
    }
}
