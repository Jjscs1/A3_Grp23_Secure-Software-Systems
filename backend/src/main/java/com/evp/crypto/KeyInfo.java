package com.evp.crypto;

/** PUBLIC data only: what the browser needs to seal a ballot. */
public record KeyInfo(String kid, String alg, String publicKey, String fingerprint) {
}
