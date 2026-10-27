package com.evp.dataprotection;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Keyed HMAC-SHA-256 of a normalised value, so an encrypted column (e.g. email) can still be looked up by equality
 * without storing it in the clear. A stolen database alone cannot be dictionary-attacked without the index key.
 */
public final class BlindIndex {
    private final SecretKey key;

    public BlindIndex(DataKeys keys) {
        this.key = keys.indexKey();
    }

    public String of(String column, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            String norm = value.trim().toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(mac.doFinal((column + "|" + norm).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new DataProtectionException("index failed");
        }
    }
}
