package com.evp.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeyringTest {

    /** Built at runtime so no key material is ever stored in the repository. */
    private static String pkcs8Pem() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(g.generateKeyPair().getPrivate().getEncoded());
        return "-----BEGIN " + "PRIVATE KEY-----\n" + body + "\n-----END " + "PRIVATE KEY-----\n";
    }

    @Test
    void loadsPemFilesAndPicksActive(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("2026-1.pem"), pkcs8Pem());
        Files.writeString(dir.resolve("2026-2.pem"), pkcs8Pem());
        Files.writeString(dir.resolve("notes.txt"), "ignored");
        ElectionKeyring ring = KeyringLoader.fromDirectory(dir, "2026-2");
        assertEquals("2026-2", ring.active().kid());
        assertNotNull(ring.get("2026-1"));
        ring.setActive("2026-1");
        assertEquals("2026-1", ring.active().kid());
        assertThrows(IllegalArgumentException.class, () -> ring.setActive("nope"));
    }

    @Test
    void failsClosedWhenNotConfigured(@TempDir Path dir) {
        assertThrows(IllegalStateException.class, () -> KeyringLoader.fromDirectory(dir, "x"));
        assertThrows(IllegalStateException.class, () -> KeyringLoader.fromDirectory(dir.resolve("missing"), "x"));
        assertThrows(IllegalStateException.class, () -> new KeyringHolder("", "", false).get());
        assertThrows(IllegalStateException.class, () -> new KeyringHolder(dir.toString(), "x", false).get());
    }

    @Test
    void activeKidMustExist(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.pem"), pkcs8Pem());
        assertThrows(IllegalArgumentException.class, () -> KeyringLoader.fromDirectory(dir, "b"));
    }

    @Test
    void badKeyFileNameRejected(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("evil name.pem"), pkcs8Pem());
        assertThrows(IllegalStateException.class, () -> KeyringLoader.fromDirectory(dir, "x"));
    }

    @Test
    void publicInfoNeverContainsPrivateMaterial() throws Exception {
        ElectionKeyring ring = KeyringLoader.ephemeral("e1", 2048);
        KeyInfo info = ring.publicInfo();
        assertEquals("e1", info.kid());
        assertEquals(RsaOaepAesGcm.ID, info.alg());
        assertEquals(64, info.fingerprint().length());
        assertFalse(info.toString().contains("PRIVATE"));
        assertTrue(info.publicKey().length() > 300);
        assertEquals("ElectionKey[e1]", ring.active().toString()); // toString never prints key material
    }

    @Test
    void holderLoadsOnceAndLazily() {
        KeyringHolder h = new KeyringHolder("", "e2", true);
        ElectionKeyring a = h.get();
        assertSame(a, h.get());
        assertEquals("e2", a.active().kid());
    }
}
