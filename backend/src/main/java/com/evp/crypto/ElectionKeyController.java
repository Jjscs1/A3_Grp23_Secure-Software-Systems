package com.evp.crypto;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoint: the election PUBLIC key, algorithm id and fingerprint. Never private material. */
@RestController
@RequestMapping("/api/election")
public class ElectionKeyController {
    private final EnvelopeService service;

    public ElectionKeyController(EnvelopeService service) {
        this.service = service;
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/key")
    public KeyInfo key() {
        return service.publicKeyInfo();
    }
}
