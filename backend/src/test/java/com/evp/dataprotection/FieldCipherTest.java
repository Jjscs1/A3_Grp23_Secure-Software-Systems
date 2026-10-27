package com.evp.dataprotection;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FieldCipherTest {
    static String b64() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    static DataKeys keys(String keyList, String active) {
        return DataKeys.fromEnv(Map.of("EVP_DATA_KEYS", keyList, "EVP_DATA_ACTIVE", active, "EVP_INDEX_KEY", b64()));
    }

    static final String K1 = b64();
    static final String K2 = b64();

    @Test
    void roundTripAndNoPlaintextInStoredValue() {
        FieldCipher c = new FieldCipher(keys("k1:" + K1, "k1"));
        String ctx = FieldCipher.context("voter", "address", 7);
        String stored = c.encrypt("1 George St, Sydney", ctx);
        assertTrue(stored.startsWith("v1.k1."));
        assertFalse(stored.contains("George"));
        assertEquals("1 George St, Sydney", c.decrypt(stored, ctx));
    }

    @Test
    void ivIsFreshEveryTime() {
        FieldCipher c = new FieldCipher(keys("k1:" + K1, "k1"));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            assertTrue(seen.add(c.encrypt("same", "ctx")));
        }
    }

    @Test
    void ciphertextCannotBeMovedToAnotherRowOrColumn() {
        FieldCipher c = new FieldCipher(keys("k1:" + K1, "k1"));
        String stored = c.encrypt("secret", FieldCipher.context("voter", "address", 1));
        assertThrows(DataProtectionException.class, () -> c.decrypt(stored, FieldCipher.context("voter", "address", 2)));
        assertThrows(DataProtectionException.class, () -> c.decrypt(stored, FieldCipher.context("voter", "email", 1)));
    }

    @Test
    void tamperingAndGarbageAreRejected() {
        FieldCipher c = new FieldCipher(keys("k1:" + K1, "k1"));
        String stored = c.encrypt("secret", "x");
        String[] p = stored.split("\\.");
        String flipped = p[0] + "." + p[1] + "." + p[2] + "." + (p[3].charAt(0) == 'A' ? 'B' : 'A') + p[3].substring(1);
        assertThrows(DataProtectionException.class, () -> c.decrypt(flipped, "x"));
        assertThrows(DataProtectionException.class, () -> c.decrypt("plaintext", "x"));
        assertThrows(DataProtectionException.class, () -> c.decrypt("v1.k1.AAAA.AAAA", "x"));
        assertThrows(DataProtectionException.class, () -> c.decrypt("v1.nope." + p[2] + "." + p[3], "x"));
    }

    @Test
    void keyIdIsAuthenticatedSoItCannotBeSwapped() {
        FieldCipher c = new FieldCipher(keys("k1:" + K1 + ",k2:" + K2, "k1"));
        String stored = c.encrypt("secret", "x");
        String swapped = stored.replace("v1.k1.", "v1.k2.");
        assertThrows(DataProtectionException.class, () -> c.decrypt(swapped, "x"));
    }

    @Test
    void wrongKeyCannotDecrypt() {
        String stored = new FieldCipher(keys("k1:" + K1, "k1")).encrypt("secret", "x");
        FieldCipher other = new FieldCipher(keys("k1:" + b64(), "k1"));
        assertThrows(DataProtectionException.class, () -> other.decrypt(stored, "x"));
    }

    @Test
    void rotationKeepsOldRowsReadableAndRekeyMovesThem() {
        String old = new FieldCipher(keys("k1:" + K1, "k1")).encrypt("secret", "x");
        FieldCipher now = new FieldCipher(keys("k1:" + K1 + ",k2:" + K2, "k2"));
        assertEquals("secret", now.decrypt(old, "x"));
        assertTrue(now.needsRekey(old));
        String moved = now.rekey(old, "x");
        assertTrue(moved.startsWith("v1.k2."));
        assertFalse(now.needsRekey(moved));
        assertSame(moved, now.rekey(moved, "x"));
        assertEquals("secret", now.decrypt(moved, "x"));
    }

    @Test
    void nullStaysNull() {
        FieldCipher c = new FieldCipher(keys("k1:" + K1, "k1"));
        assertNull(c.encrypt(null, "x"));
        assertNull(c.decrypt(null, "x"));
    }

    @Test
    void errorsNeverLeakPlaintextOrKeys() {
        FieldCipher c = new FieldCipher(keys("k1:" + K1, "k1"));
        String stored = c.encrypt("TOPSECRETVALUE", "x");
        DataProtectionException e = assertThrows(DataProtectionException.class, () -> c.decrypt(stored, "y"));
        assertFalse(e.getMessage().contains("TOPSECRET"));
        assertFalse(keys("k1:" + K1, "k1").toString().contains(K1));
    }

    @Test
    void blindIndexIsDeterministicNormalisedKeyedAndColumnScoped() {
        DataKeys k = keys("k1:" + K1, "k1");
        BlindIndex i = new BlindIndex(k);
        assertEquals(i.of("email", "A@B.com "), i.of("email", "a@b.com"));
        assertNotEquals(i.of("email", "a@b.com"), i.of("phone", "a@b.com"));
        assertNotEquals(i.of("email", "a@b.com"), new BlindIndex(keys("k1:" + K1, "k1")).of("email", "a@b.com"));
        assertEquals(64, i.of("email", "x").length());
        assertFalse(i.of("email", "a@b.com").contains("a@b.com"));
    }
}
