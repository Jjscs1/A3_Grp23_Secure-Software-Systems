package com.evp.enrolment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * SEC-08: exact match key for the public enrolment check.
 *
 * The key is HMAC-SHA256 over the normalised full name, date of birth and postcode.
 * Only a voter who supplies all three exactly gets a match, so a partial match
 * (right name, wrong postcode) is indistinguishable from "not enrolled". Because the
 * column holds an HMAC rather than the raw values, the lookup also works when the
 * personal details themselves are stored encrypted (SEC-19).
 *
 * The HMAC secret must come from a secret store or environment variable, never the repo.
 */
public final class EnrolmentLookupKey {

    private static final String ALGORITHM = "HmacSHA256";
    private static final char SEPARATOR = '\u001F'; // ASCII unit separator, cannot appear in valid input
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKeySpec secret;

    public EnrolmentLookupKey(byte[] secretBytes) {
        Objects.requireNonNull(secretBytes, "secretBytes");
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "Enrolment lookup secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.secret = new SecretKeySpec(secretBytes.clone(), ALGORITHM);
    }

    /** Computes the lookup key. Stored on the voter at registration and recomputed for each check. */
    public String compute(String fullName, LocalDate dateOfBirth, String postcode) {
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        String canonical = normaliseName(fullName) + SEPARATOR
                + dateOfBirth + SEPARATOR
                + normalisePostcode(postcode);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secret);
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /**
     * Unicode NFKC, trimmed, internal whitespace collapsed to one space, lower case.
     * "  Jane   SMITH " and "jane smith" give the same key; "Jane Smit" does not.
     */
    static String normaliseName(String fullName) {
        Objects.requireNonNull(fullName, "fullName");
        String nfkc = Normalizer.normalize(fullName, Normalizer.Form.NFKC);
        return nfkc.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String normalisePostcode(String postcode) {
        Objects.requireNonNull(postcode, "postcode");
        return postcode.trim();
    }
}
