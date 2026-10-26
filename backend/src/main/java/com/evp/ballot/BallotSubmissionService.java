package com.evp.ballot;

import com.evp.token.BlindSigner;
import com.evp.token.DuplicateTokenException;
import com.evp.token.InvalidTokenException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * SEC-11: accepts a ballot only with a valid, unused voting token.
 *
 * The endpoint is anonymous on purpose. The voter does not log in to submit, so the
 * submission carries no identity, only a token proving "someone eligible was issued
 * this, once".
 *
 * Race handling: if two submissions of the same token arrive together, the database's
 * UNIQUE constraint lets exactly one through. PostgreSQL makes the second wait and then
 * reports a duplicate. Some databases (H2, used in tests) fail the second one straight
 * away with a concurrency error instead, so that case is retried once in a fresh
 * transaction, where it then reports the duplicate. Nothing commits in a failed
 * attempt, so the retry is safe.
 */
@Service
public class BallotSubmissionService {

    public static final int SERIAL_BYTES = 32;
    private static final long RETRY_DELAY_MS = 50;

    private final BlindSigner signer;
    private final BallotWriter writer;

    public BallotSubmissionService(BlindSigner signer, BallotWriter writer) {
        this.signer = signer;
        this.writer = writer;
    }

    public void submit(byte[] serial, byte[] signature, String encryptedBallotEnvelope) {
        if (serial == null || serial.length != SERIAL_BYTES) {
            throw new IllegalArgumentException("Serial must be " + SERIAL_BYTES + " bytes");
        }
        if (!signer.verify(serial, signature)) {
            throw new InvalidTokenException();
        }
        String serialHash = sha256Hex(serial);
        try {
            writer.spendAndStore(serialHash, encryptedBallotEnvelope);
        } catch (DataIntegrityViolationException alreadySpent) {
            throw new DuplicateTokenException();
        } catch (DataAccessException concurrentAttempt) {
            pause();
            try {
                writer.spendAndStore(serialHash, encryptedBallotEnvelope);
            } catch (DataIntegrityViolationException alreadySpent) {
                throw new DuplicateTokenException();
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
