package com.evp.crypto;

import java.security.GeneralSecurityException;

/** One sealing scheme. Add a new implementation + register it to upgrade the cipher. */
public interface EnvelopeAlgorithm {
    String id();

    /** Cheap structural check (sizes) done at cast time without decrypting. */
    boolean validate(BallotEnvelope e, ElectionKey key);

    /** Opens the envelope. Called only at count time. */
    byte[] decrypt(BallotEnvelope e, ElectionKey key) throws GeneralSecurityException;
}
