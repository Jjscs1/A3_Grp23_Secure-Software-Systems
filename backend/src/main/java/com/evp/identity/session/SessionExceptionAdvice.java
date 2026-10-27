package com.evp.identity.session;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionExceptionAdvice {
    @ExceptionHandler(SessionRejected.class)
    public ResponseEntity<Map<String,String>> ended() {
        return ResponseEntity.status(401).body(Map.of("error", "SESSION_ENDED"));
    }
    @ExceptionHandler(ReauthenticationRequired.class)
    public ResponseEntity<Map<String,String>> reauthenticate() {
        return ResponseEntity.status(403).body(Map.of("error", "REAUTH_REQUIRED"));
    }
}
