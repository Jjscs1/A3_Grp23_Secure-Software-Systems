package com.evp.crypto;

import java.nio.file.Path;
import java.security.GeneralSecurityException;

/**
 * Loads the keyring on FIRST USE and fails closed (clear error, no insecure fallback) if it is not
 * configured. Lazy on purpose: contexts that never touch ballot crypto (other teams' tests) still start.
 */
public final class KeyringHolder {
    private final String dir;
    private final String activeKid;
    private final boolean ephemeral;
    private ElectionKeyring ring;

    public KeyringHolder(String dir, String activeKid, boolean ephemeral) {
        this.dir = dir;
        this.activeKid = activeKid;
        this.ephemeral = ephemeral;
    }

    public synchronized ElectionKeyring get() {
        if (ring == null) {
            try {
                if (ephemeral) {
                    ring = KeyringLoader.ephemeral(activeKid == null || activeKid.isBlank() ? "ephemeral-1" : activeKid, 3072);
                } else {
                    if (dir == null || dir.isBlank() || activeKid == null || activeKid.isBlank()) {
                        throw new IllegalStateException(
                                "Ballot encryption keys are not configured (EVP_BALLOT_KEYRING_DIR, EVP_BALLOT_ACTIVE_KID)");
                    }
                    ring = KeyringLoader.fromDirectory(Path.of(dir), activeKid);
                }
            } catch (java.io.IOException | GeneralSecurityException e) {
                throw new IllegalStateException("Ballot encryption keys could not be loaded", e);
            }
        }
        return ring;
    }
}
