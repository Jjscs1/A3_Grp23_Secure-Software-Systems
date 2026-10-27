package com.evp.results;

import com.evp.web.ApiError;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SEC-18 REST API.
 *
 * POST /api/results/{electionId}/publish              ADMIN (elevated) sign the next version
 * GET  /api/public/results/public-key                 anyone           the key to verify with
 * GET  /api/public/results/{electionId}               anyone           list of sequence numbers
 * GET  /api/public/results/{electionId}/latest        anyone           newest signed publication
 * GET  /api/public/results/{electionId}/{sequence}    anyone           a specific version
 *
 * Every endpoint has its own @PreAuthorize (SEC-03). The public GETs are covered by the
 * GET /api/public/** rule in config/SecurityConfig; the publish POST has its own line there.
 */
@RestController
@RequestMapping("/api")
public class ResultsController {

    static final String ELEVATED_ADMIN = "hasRole('ADMIN') and @adminElevation.isActive(#jwt)";

    private final ResultsPublisher publisher;

    public ResultsController(ResultsPublisher publisher) {
        this.publisher = publisher;
    }

    public record PublishRequest(@NotEmpty @Size(max = 200) List<@NotNull Long> tallyIds) {
    }

    public record PublicKeyResponse(String algorithm, String keyId, String publicKeyPem) {
    }

    @PostMapping("/results/{electionId}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ELEVATED_ADMIN)
    public JsonNode publish(@PathVariable long electionId, @Valid @RequestBody PublishRequest body,
                            @AuthenticationPrincipal Jwt jwt, Principal admin) {
        return publisher.publish(electionId, body.tallyIds(), admin.getName());
    }

    @GetMapping("/public/results/public-key")
    @PreAuthorize("permitAll()")
    public PublicKeyResponse publicKey() {
        return new PublicKeyResponse(ResultsSigning.ALGORITHM, publisher.keyId(), publisher.publicKeyPem());
    }

    @GetMapping("/public/results/{electionId}")
    @PreAuthorize("permitAll()")
    public List<Long> sequences(@PathVariable long electionId) {
        return publisher.sequences(electionId);
    }

    @GetMapping("/public/results/{electionId}/latest")
    @PreAuthorize("permitAll()")
    public JsonNode latest(@PathVariable long electionId) {
        return publisher.latest(electionId);
    }

    @GetMapping("/public/results/{electionId}/{sequence}")
    @PreAuthorize("permitAll()")
    public JsonNode bySequence(@PathVariable long electionId, @PathVariable long sequence) {
        return publisher.bySequence(electionId, sequence);
    }

    @ExceptionHandler(ResultsNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND"));
    }
}