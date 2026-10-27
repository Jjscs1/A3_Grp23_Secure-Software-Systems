package com.evp.crypto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sealed ballot as produced in the voter's browser (SEC-12). The envelope NAMES its algorithm and key id
 * so ciphers and keys can be upgraded without changing routes, schema or stored data.
 * ek = wrapped content key, iv = AES-GCM nonce, ct = ciphertext||tag (all standard base64).
 */
public record BallotEnvelope(
        @NotNull @Min(1) @Max(1) Integer v,
        @NotNull @Pattern(regexp = "^[A-Za-z0-9+._-]{3,40}$") String alg,
        @NotNull @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$") String kid,
        @NotNull @Size(min = 4, max = 1400) @Pattern(regexp = "^[A-Za-z0-9+/]+={0,2}$") String ek,
        @NotNull @Size(min = 4, max = 24) @Pattern(regexp = "^[A-Za-z0-9+/]+={0,2}$") String iv,
        @NotNull @Size(min = 4, max = 4096) @Pattern(regexp = "^[A-Za-z0-9+/]+={0,2}$") String ct) {
}
