package com.evp.config;

import com.evp.security.SecurityHeaders;
import com.evp.identity.access.AdminElevation;
import java.util.Set;
import com.evp.identity.ratelimit.RequestRateLimiter;
import com.evp.identity.ratelimit.IpRateLimitFilter;
import com.evp.identity.ratelimit.AccountRateLimitFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import com.evp.identity.session.BoundSessionFilter;
import com.evp.identity.session.BoundSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
    public SecurityFilterChain apiSecurity(HttpSecurity http, BoundSessionService sessions, RequestRateLimiter rateLimiter, AdminElevation elevation,
            @Value("${evp.rate-limit.enabled:true}") boolean rateLimitEnabled,
            @Value("${evp.session.enabled:true}") boolean sessionEnabled,
            @Value("${evp.session.origin:http://localhost:5173}") String appOrigin)
            throws Exception {

        SecurityHeaders.configure(http);

        http
            .cors(Customizer.withDefaults())
            // Cookies never authenticate alone: every protected request requires explicit DPoP.
            // BoundSessionFilter also checks Origin for authenticated mutation requests.
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
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/candidates", "/api/change-requests",
                    "/api/session/status", "/api/admin/elevation").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/tokens", "/api/change-requests",
                    "/api/change-requests/{id}/approvals", "/api/change-requests/{id}/rejection",
                    "/api/session/start", "/api/session/activity", "/api/session/end",
                    "/api/session/prepare-cast").authenticated()
                // New routes require both a method policy and an explicit method/path entry here.
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
                    .jwtAuthenticationConverter(keycloakRoles(elevation))));

        if (rateLimitEnabled) {
            // IP budget before authentication/body parsing; account budget only after authentication.
            http.addFilterBefore(new IpRateLimitFilter(rateLimiter), LogoutFilter.class);
            http.addFilterBefore(new AccountRateLimitFilter(rateLimiter), AuthorizationFilter.class);
        }
        if (sessionEnabled) {
            http.addFilterBefore(new BoundSessionFilter(sessions, appOrigin), AuthorizationFilter.class);
        }
        return http.build();
    }

    public static JwtAuthenticationConverter keycloakRoles(AdminElevation elevation) {
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
                .map(role -> role.toUpperCase(Locale.ROOT))
                .filter(Set.of("VOTER", "AEC_OFFICER", "DELEGATE", "ADMIN", "AUDITOR")::contains)
                .filter(role -> !role.equals("ADMIN") || elevation.isActive(jwt))
                .distinct()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
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
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}
