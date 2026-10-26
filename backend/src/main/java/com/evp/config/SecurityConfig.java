package com.evp.config;

import com.evp.security.SecurityHeaders;
import jakarta.servlet.DispatcherType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * MERGE NOTE: SecurityConfig belongs to the Identity and access member (SEC-01, SEC-03,
 * SEC-04). This version is just enough to run and test SEC-05/07/08/10/11 on their own.
 * When merging, keep from this file:
 *   1. SecurityHeaders.configure(http);                        (SEC-07)
 *   2. permitAll for POST /api/public/enrolment-check          (SEC-08)
 *   3. permitAll for GET /api/tokens/public-key and POST /api/ballots   (SEC-11)
 *   4. @EnableMethodSecurity, since SEC-05 and SEC-11 use @PreAuthorize
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        SecurityHeaders.configure(http);
        http
                // Bearer tokens in the Authorization header, no cookies, so CSRF does not apply.
                // If SEC-04 moves tokens into cookies, CSRF protection must be turned back on.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/enrolment-check").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tokens/public-key").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/ballots").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRoles())));
        return http.build();
    }

    /**
     * Maps Keycloak realm roles (realm_access.roles) to ROLE_ authorities, and uses the
     * username as the principal name so Voter.oidcSubject can hold readable usernames
     * (needed by DemoVoterSeeder). SEC-01's owner may switch this back to "sub" for
     * production, since usernames can be changed in Keycloak and subject IDs cannot.
     */
    static JwtAuthenticationConverter keycloakRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Object roles = realmAccess == null ? null : realmAccess.get("roles");
            if (!(roles instanceof Collection<?> names)) {
                return List.<GrantedAuthority>of();
            }
            return names.stream()
                    .map(String::valueOf)
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                    .toList();
        });
        return converter;
    }
}
