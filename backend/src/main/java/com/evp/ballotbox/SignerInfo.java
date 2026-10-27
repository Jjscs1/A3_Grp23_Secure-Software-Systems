package com.evp.ballotbox;

/** PUBLIC verification data for the ballot-box signing key. */
public record SignerInfo(String kid, String alg, String publicKey, String fingerprint) {
}
