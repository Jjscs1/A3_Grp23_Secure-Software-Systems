package com.evp.crypto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Server side of SEC-12. At CAST time the server only checks that an envelope is well-formed and sealed
 * with a currently accepted algorithm and key; it cannot read the ballot. At COUNT time {@link #open}
 * unseals using the key named in the envelope.
 */
public final class EnvelopeService {
    private static final Pattern ALG = Pattern.compile("^[A-Za-z0-9+._-]{3,40}$");
    private static final Pattern KID = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final Pattern B64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    private final AlgorithmRegistry registry;
    private final Supplier<ElectionKeyring> ring;
    private final ObjectMapper strict = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public EnvelopeService(AlgorithmRegistry registry, Supplier<ElectionKeyring> ring) {
        this.registry = registry;
        this.ring = ring;
    }

    public KeyInfo publicKeyInfo() {
        return ring.get().publicInfo();
    }

    /** Strict parse of the JSON text received from the browser. */
    public BallotEnvelope parse(String json) throws InvalidEnvelopeException {
        if (json == null || json.length() > 8192) {
            throw new InvalidEnvelopeException("malformed_envelope");
        }
        try {
            BallotEnvelope e = strict.readValue(json, BallotEnvelope.class);
            checkFormat(e);
            return e;
        } catch (IOException ex) {
            throw new InvalidEnvelopeException("malformed_envelope");
        }
    }

    static void checkFormat(BallotEnvelope e) throws InvalidEnvelopeException {
        boolean ok = e != null && e.v() != null && e.v() == 1
                && e.alg() != null && ALG.matcher(e.alg()).matches()
                && e.kid() != null && KID.matcher(e.kid()).matches()
                && fits(e.ek(), 1400) && fits(e.iv(), 24) && fits(e.ct(), 4096);
        if (!ok) {
            throw new InvalidEnvelopeException("malformed_envelope");
        }
    }

    private static boolean fits(String s, int max) {
        return s != null && s.length() >= 4 && s.length() <= max && B64.matcher(s).matches();
    }

    public BallotEnvelope requireValidForCast(String json) throws InvalidEnvelopeException {
        BallotEnvelope e = parse(json);
        requireValidForCast(e);
        return e;
    }

    /** New ballots must use the ACTIVE key and an ACTIVE algorithm. */
    public void requireValidForCast(BallotEnvelope e) throws InvalidEnvelopeException {
        checkFormat(e);
        ElectionKeyring r = ring.get();
        ElectionKey key = r.get(e.kid());
        EnvelopeAlgorithm algorithm = registry.get(e.alg());
        if (key == null || !key.kid().equals(r.active().kid())) {
            throw new InvalidEnvelopeException("unknown_or_retired_key");
        }
        if (algorithm == null || registry.status(e.alg()) != AlgorithmRegistry.Status.ACTIVE || !e.alg().equals(key.alg())) {
            throw new InvalidEnvelopeException("unsupported_algorithm");
        }
        if (!algorithm.validate(e, key)) {
            throw new InvalidEnvelopeException("malformed_envelope");
        }
    }

    /** Count time: unseal with the key named in the envelope (retired keys and deprecated algorithms still work). */
    public List<Long> open(BallotEnvelope e) throws InvalidEnvelopeException {
        ElectionKey key = ring.get().get(e.kid());
        EnvelopeAlgorithm algorithm = registry.get(e.alg());
        if (key == null || algorithm == null) {
            throw new InvalidEnvelopeException("cannot_open");
        }
        try {
            long[] ids = strict.readValue(algorithm.decrypt(e, key), long[].class);
            List<Long> out = new ArrayList<>(ids.length);
            for (long id : ids) {
                out.add(id);
            }
            return out;
        } catch (GeneralSecurityException | IOException | RuntimeException ex) {
            throw new InvalidEnvelopeException("cannot_open"); // tampered, wrong key, bad content: all informal
        }
    }

    /** A formal House of Representatives ballot ranks EVERY candidate exactly once. */
    public static boolean isFormal(List<Long> preferences, Set<Long> candidateIds) {
        return preferences != null && preferences.size() == candidateIds.size()
                && new HashSet<>(preferences).size() == preferences.size()
                && candidateIds.containsAll(preferences);
    }
}
