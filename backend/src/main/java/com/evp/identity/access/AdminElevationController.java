package com.evp.identity.access;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports an existing grant. It cannot create, extend or approve one. */
@RestController
public class AdminElevationController {
    @GetMapping("/api/admin/elevation")
    @PreAuthorize("hasRole('ADMIN') and @adminElevation.isActive(#jwt)")
    public Map<String, Object> status(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("role", "ADMIN", "expiresAt", jwt.getClaims().get(AdminElevation.END));
    }
}
