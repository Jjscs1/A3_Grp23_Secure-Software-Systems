package com.evp.token;

import com.evp.token.TokenDtos.IssueTokenRequest;
import com.evp.identity.session.SessionBoundTokenIssuance;
import com.evp.identity.session.BoundSessionFilter;
import com.evp.identity.session.BoundSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseCookie;
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

    private final SessionBoundTokenIssuance issuance;
    private final BlindSigner signer;

    public TokenController(SessionBoundTokenIssuance issuance, BlindSigner signer) {
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
    public IssueTokenResponse issue(@Valid @RequestBody IssueTokenRequest request, Principal voter,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest http, HttpServletResponse response) {
        byte[] blinded = Base64.getDecoder().decode(request.blindedMessage());
        byte[] blindSignature = issuance.issue(jwt, BoundSessionFilter.cookie(http),
            voter.getName(), request.division(), blinded);
        response.addHeader("Set-Cookie", ResponseCookie.from(BoundSessionService.COOKIE, "")
            .secure(true).httpOnly(true).sameSite("Strict").path("/").maxAge(0).build().toString());
        return new IssueTokenResponse(Base64.getEncoder().encodeToString(blindSignature));
    }
}
