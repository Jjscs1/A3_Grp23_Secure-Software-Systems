package com.evp.ballot;

import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SEC-11: anonymous ballot submission, permitted without login in SecurityConfig.
 * SEC-13 (another member) must make sure request bodies on this path are never logged.
 */
@RestController
@RequestMapping("/api/ballots")
public class BallotController {

    private final BallotSubmissionService submissions;

    public BallotController(BallotSubmissionService submissions) {
        this.submissions = submissions;
    }

    public record SubmitBallotRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9+/=]+$") String serial,
            @NotBlank @Size(max = 2048) @Pattern(regexp = "^[A-Za-z0-9+/=]+$") String signature,
            @NotBlank @Size(max = 65536) String encryptedBallot) {
    }

    public record SubmitBallotResponse(String status) {
    }

    @PostMapping
    @PreAuthorize("permitAll()")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitBallotResponse submit(@Valid @RequestBody SubmitBallotRequest request) {
        Base64.Decoder b64 = Base64.getDecoder();
        submissions.submit(b64.decode(request.serial()), b64.decode(request.signature()), request.encryptedBallot());
        return new SubmitBallotResponse("RECORDED");
    }
}
