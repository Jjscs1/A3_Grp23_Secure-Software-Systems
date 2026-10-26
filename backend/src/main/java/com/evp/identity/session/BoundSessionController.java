package com.evp.identity.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class BoundSessionController {
    private final BoundSessionService sessions;
    public BoundSessionController(BoundSessionService sessions) { this.sessions = sessions; }
    public record StartRequest(@NotBlank @Size(max = 16384) String idToken) {}
    @PostMapping("/start")
    public ResponseEntity<Void> start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        String cookie = sessions.start(jwt, body.idToken(), BoundSessionFilter.cookie(request));
        response.addHeader("Set-Cookie", cookie(cookie, 600));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/activity")
    public ResponseEntity<Void> activity(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        sessions.activity(jwt, BoundSessionFilter.cookie(request));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/end")
    public ResponseEntity<Void> end(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            HttpServletResponse response) {
        sessions.end(jwt, BoundSessionFilter.cookie(request));
        response.addHeader("Set-Cookie", cookie("", 0));
        return ResponseEntity.noContent().build();
    }
    private String cookie(String value, long seconds) {
        return ResponseCookie.from(BoundSessionService.COOKIE, value).path("/").secure(true)
            .httpOnly(true).sameSite("Strict").maxAge(seconds).build().toString();
    }
    @ExceptionHandler(SessionRejected.class)
    public ResponseEntity<java.util.Map<String,String>> rejected() {
        return ResponseEntity.status(401).body(java.util.Map.of("error", "SESSION_ENDED"));
    }
}
