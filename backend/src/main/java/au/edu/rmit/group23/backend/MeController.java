package au.edu.rmit.group23.backend;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        return new UserInfo(
            jwt.getSubject(),
            jwt.getClaimAsString("preferred_username")
        );
    }

    public record UserInfo(String subject, String username) {
    }
}