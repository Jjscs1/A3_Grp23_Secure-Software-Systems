package com.evp.token;

import com.evp.token.TokenDtos.IssueTokenRequest;
import com.evp.token.TokenDtos.IssueTokenResponse;
import com.evp.token.TokenDtos.PublicKeyResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Base64;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenIssuanceService issuance;
    private final BlindSigner signer;

    public TokenController(TokenIssuanceService issuance, BlindSigner signer) {
        this.issuance = issuance;
        this.signer = signer;
    }

    /** Public: the browser needs the election public key to blind its serial. */
    @GetMapping("/public-key")
    public PublicKeyResponse publicKey() {
        return new PublicKeyResponse(BlindSigner.SUITE, Base64.getEncoder().encodeToString(signer.publicKeySpki()));
    }

    /** Voter only. The voter's identity comes from their login, never from the request body. */
    @PostMapping
    @PreAuthorize("hasRole('VOTER')")
    public IssueTokenResponse issue(@Valid @RequestBody IssueTokenRequest request, Principal voter) {
        byte[] blinded = Base64.getDecoder().decode(request.blindedMessage());
        byte[] blindSignature = issuance.issue(voter.getName(), request.division(), blinded);
        return new IssueTokenResponse(Base64.getEncoder().encodeToString(blindSignature));
    }
}
