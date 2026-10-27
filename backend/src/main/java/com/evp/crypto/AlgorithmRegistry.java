package com.evp.crypto;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crypto-agility: algorithms are looked up BY NAME from the envelope.
 * ACTIVE = may seal and count. DEPRECATED = no new ballots, but earlier ones can still be counted.
 */
public final class AlgorithmRegistry {
    public enum Status { ACTIVE, DEPRECATED }

    private static final class Entry {
        final EnvelopeAlgorithm algorithm;
        volatile Status status;

        Entry(EnvelopeAlgorithm algorithm, Status status) {
            this.algorithm = algorithm;
            this.status = status;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public static AlgorithmRegistry withDefaults() {
        AlgorithmRegistry r = new AlgorithmRegistry();
        r.register(new RsaOaepAesGcm(), Status.ACTIVE);
        return r;
    }

    public void register(EnvelopeAlgorithm algorithm, Status status) {
        entries.put(algorithm.id(), new Entry(algorithm, status));
    }

    public void setStatus(String id, Status status) {
        Entry e = entries.get(id);
        if (e == null) {
            throw new IllegalArgumentException("unknown algorithm");
        }
        e.status = status;
    }

    public EnvelopeAlgorithm get(String id) {
        Entry e = entries.get(id);
        return e == null ? null : e.algorithm;
    }

    public Status status(String id) {
        Entry e = entries.get(id);
        return e == null ? null : e.status;
    }

    public Set<String> ids() {
        return entries.keySet();
    }
}
