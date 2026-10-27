package com.evp.trustee;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TrusteeController {

    private final ThresholdDecryptionService service;

    public TrusteeController(ThresholdDecryptionService service) {
        this.service = service;
    }

    public record KeyCeremonyResponse(int threshold, int trustees, List<String> shares, String instructions) {
    }

    public record ElectionKeyResponse(String algorithm, String publicKeyPem) {
    }

    public record PollStatusResponse(boolean closed) {
    }

    public record DecryptRequest(@NotNull @Size(max = ThresholdDecryptionService.TRUSTEES)
                                 List<@NotNull @Size(max = 200) String> shares) {
    }

    @PostMapping("/trustees/key-ceremony")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ELECTION_ADMIN')")
    public KeyCeremonyResponse keyCeremony(Principal admin) {
        List<String> shares = service.keyCeremony(admin.getName());
        return new KeyCeremonyResponse(ThresholdDecryptionService.THRESHOLD, ThresholdDecryptionService.TRUSTEES,
                shares, "Give one share to each trustee now. They are not stored and cannot be shown again.");
    }

    @GetMapping("/trustees/election-key")
    public ElectionKeyResponse electionKey() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(
                service.electionPublicKey().getEncoded());
        return new ElectionKeyResponse(BallotEnvelope.ALGORITHM,
                "BEGIN PUBLIC KEY\n" + base64 + "\nEND PUBLIC KEY\n");
    }

    @PostMapping("/poll/close")
    @PreAuthorize("hasRole('ELECTION_ADMIN')")
    public PollStatusResponse closePoll(Principal admin) {
        service.closePoll(admin.getName());
        return new PollStatusResponse(true);
    }

    @GetMapping("/poll/status")
    public PollStatusResponse pollStatus() {
        return new PollStatusResponse(service.pollIsClosed());
    }

    @PostMapping("/tally/decrypt")
    @PreAuthorize("hasRole('TRUSTEE')")
    public ThresholdDecryptionService.DecryptionResult decrypt(@Valid @RequestBody DecryptRequest request,
                                                               Principal trustee) {
        return service.decryptBallotBox(request.shares(), trustee.getName());
    }
}