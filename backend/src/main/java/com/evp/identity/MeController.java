package com.evp.identity;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    public UserInfo me(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {

        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .distinct()
            .sorted()
            .toList();

        return new UserInfo(
            jwt.getSubject(),
            jwt.getClaimAsString("preferred_username"),
            roles
        );
    }

    public record UserInfo(
        String subject,
        String username,
        List<String> roles
    ) {
    }
}
