package com.evp.ballot;

/**
 * Integration point with the Ballot casting and cryptography member, who owns SEC-12
 * (client-side encrypted envelope), SEC-13 (separate ballot schema, hourly timestamps)
 * and SEC-14 (Merkle log and tracking codes).
 *
 * SEC-11 decides whether a ballot may be accepted; the sink decides how it is stored.
 * Until their implementation exists, {@link JpaBallotSink} stores the envelope as is.
 * When theirs is ready, delete JpaBallotSink and StoredBallot, or mark their bean @Primary.
 */
public interface BallotSink {

    /** Stores an already encrypted ballot envelope. Must not record anything about the voter. */
    void store(String encryptedBallotEnvelope);
}
