package com.evp.crypto;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Many key ids can exist; exactly one is ACTIVE for sealing new ballots. Retired keys stay loaded so
 * ballots sealed earlier can still be opened at count time (key rotation).
 */
public final class ElectionKeyring {
    private final Map<String, ElectionKey> keys = new LinkedHashMap<>();
    private volatile String activeKid;

    public ElectionKeyring(Collection<ElectionKey> entries, String activeKid) {
        entries.forEach(k -> keys.put(k.kid(), k));
        if (!keys.containsKey(activeKid)) {
            throw new IllegalArgumentException("active key id is not in the keyring");
        }
        this.activeKid = activeKid;
    }

    public ElectionKey active() {
        return keys.get(activeKid);
    }

    public ElectionKey get(String kid) {
        return keys.get(kid);
    }

    public void setActive(String kid) {
        if (!keys.containsKey(kid)) {
            throw new IllegalArgumentException("unknown key id");
        }
        this.activeKid = kid;
    }

    public KeyInfo publicInfo() {
        return active().info();
    }
}
