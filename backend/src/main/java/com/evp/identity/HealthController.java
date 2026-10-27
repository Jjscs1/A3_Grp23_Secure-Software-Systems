package com.evp.identity;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    @PreAuthorize("permitAll()")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
