package com.evp.dataprotection;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM field encryption. Stored form: {@code v1.<kid>.<iv b64url>.<ciphertext+tag b64url>}.
 * The context (table, column, row id) is bound in as additional authenticated data, so a ciphertext copied
 * to another row or column fails to decrypt. A fresh random 96-bit IV is used for every encryption.
 */
public final class FieldCipher {
    private static final String PREFIX = "v1";
    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder UN64 = Base64.getUrlDecoder();

    private final DataKeys keys;

    public FieldCipher(DataKeys keys) {
        this.keys = keys;
    }

    public static String context(String table, String column, Object rowId) {
        return table + "|" + column + "|" + rowId;
    }

    public String encrypt(String plain, String context) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, keys.active(), new GCMParameterSpec(128, iv));
            c.updateAAD(aad(keys.activeId(), context));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + "." + keys.activeId() + "." + B64.encodeToString(iv) + "." + B64.encodeToString(ct);
        } catch (GeneralSecurityException e) {
            throw new DataProtectionException("encryption failed");
        }
    }

    public String decrypt(String stored, String context) {
        if (stored == null) {
            return null;
        }
        String[] p = stored.split("\\.", -1);
        if (p.length != 4 || !PREFIX.equals(p[0])) {
            throw new DataProtectionException("not an encrypted value");
        }
        SecretKey k = keys.get(p[1]);
        if (k == null) {
            throw new DataProtectionException("unknown key id");
        }
        try {
            byte[] iv = UN64.decode(p[2]);
            if (iv.length != 12) {
                throw new DataProtectionException("bad iv");
            }
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv));
            c.updateAAD(aad(p[1], context));
            return new String(c.doFinal(UN64.decode(p[3])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new DataProtectionException("decryption failed");
        }
    }

    /** True if the stored value is under an older key and should be re-encrypted. */
    public boolean needsRekey(String stored) {
        String[] p = stored == null ? new String[0] : stored.split("\\.", -1);
        return p.length == 4 && !p[1].equals(keys.activeId());
    }

    public String rekey(String stored, String context) {
        return needsRekey(stored) ? encrypt(decrypt(stored, context), context) : stored;
    }

    private static byte[] aad(String kid, String context) {
        return (PREFIX + "|" + kid + "|" + context).getBytes(StandardCharsets.UTF_8);
    }
}
