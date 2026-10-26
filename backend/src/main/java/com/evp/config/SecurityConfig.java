package com.evp.config;

import com.evp.security.SecurityHeaders;
import jakarta.servlet.DispatcherType;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http)
            throws Exception {

        SecurityHeaders.configure(http);

        http
            .cors(Customizer.withDefaults())
            // DPoP authentication only; cookies are not used in this implementation stage.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/public/enrolment-check"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/tokens/public-key"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/ballots"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                           .requestMatchers("/api/me").denyAll()
                           .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                // Spring Security 6.5 validates DPoP proofs automatically.
                // Reject Bearer so a bound token cannot bypass proof validation.
                .bearerTokenResolver(request -> {
                    String header = request.getHeader("Authorization");
                    if (header != null && !header.regionMatches(true, 0, "DPoP ", 0, 5)) {
                        throw new OAuth2AuthenticationException(
                            new OAuth2Error("invalid_token"), "DPoP authentication is required.");
                    }
                    return null;
                })
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(keycloakRoles())));

        return http.build();
    }

    static JwtAuthenticationConverter keycloakRoles() {
        JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();

        // Keep compatibility with the team's demo voter records.
        converter.setPrincipalClaimName("preferred_username");

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess =
                jwt.getClaimAsMap("realm_access");

            Object roles =
                realmAccess == null ? null : realmAccess.get("roles");

            if (!(roles instanceof Collection<?> names)) {
                return List.<GrantedAuthority>of();
            }

            return names.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> (GrantedAuthority)
                    new SimpleGrantedAuthority(
                        "ROLE_" + role.toUpperCase(Locale.ROOT)
                    ))
                .toList();
        });

        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
            List.of("http://localhost:5173")
        );
        configuration.setAllowedMethods(
            List.of("GET", "POST", "OPTIONS")
        );
        configuration.setAllowedHeaders(
            List.of("Authorization", "DPoP", "Content-Type")
        );
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}
