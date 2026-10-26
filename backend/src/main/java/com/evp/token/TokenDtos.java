package com.evp.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies for the token endpoints. Binary values are base64. */
public final class TokenDtos {

    private TokenDtos() {
    }

    public record PublicKeyResponse(String suite, String spki) {
    }

    public record IssueTokenRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9 _\\-]{2,40}$") String division,
            @NotBlank @Size(max = 2048) @Pattern(regexp = "^[A-Za-z0-9+/=]+$") String blindedMessage) {
    }

    public record IssueTokenResponse(String blindSignature) {
    }
}
