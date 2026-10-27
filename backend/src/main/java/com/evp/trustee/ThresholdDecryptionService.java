package com.evp.trustee;
import com.evp.audit.AuditLogger;
import com.evp.ballot.StoredBallot;
import com.evp.ballot.StoredBallotRepository;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThresholdDecryptionService {

    public static final int THRESHOLD = 3;
    public static final int TRUSTEES = 5;
    private static final int RSA_BITS = 3072;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ElectionKeyVaultRepository vaults;
    private final PollClosureRepository pollClosures;
    private final StoredBallotRepository ballots;
    private final AuditLogger audit;
    private final Clock clock;

    public ThresholdDecryptionService(ElectionKeyVaultRepository vaults, PollClosureRepository pollClosures,
                                      StoredBallotRepository ballots, AuditLogger audit, Clock clock) {
        this.vaults = vaults;
        this.pollClosures = pollClosures;
        this.ballots = ballots;
        this.audit = audit;
        this.clock = clock;
    }

    public record DecryptionResult(List<String> ballots, int undecryptable) {
    }

    @Transactional
    public List<String> keyCeremony(String actor) {
        if (vaults.existsById(ElectionKeyVault.SINGLETON_ID)) {
            throw new KeyCeremonyException("The key ceremony has already been held for this election.");
        }
        try {
            KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
            rsa.initialize(RSA_BITS);
            KeyPair electionKey = rsa.generateKeyPair();

            byte[] kek = new byte[Shamir.SECRET_BYTES];
            RANDOM.nextBytes(kek);
            String locked = lock(kek, electionKey.getPrivate().getEncoded());
            List<String> shares = Shamir.split(kek, THRESHOLD, TRUSTEES);
            Arrays.fill(kek, (byte) 0);

            vaults.save(new ElectionKeyVault(
                    Base64.getEncoder().encodeToString(electionKey.getPublic().getEncoded()),
                    locked, THRESHOLD, TRUSTEES, clock.instant(), actor));
            audit.record("KEY_CEREMONY_HELD", actor,
                    Map.of("threshold", String.valueOf(THRESHOLD), "trustees", String.valueOf(TRUSTEES)));
            return shares;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key ceremony failed", e);
        }
    }

    public PublicKey electionPublicKey() {
        ElectionKeyVault vault = vaults.findById(ElectionKeyVault.SINGLETON_ID)
                .orElseThrow(() -> new KeyCeremonyException("The key ceremony has not been held yet."));
        try {
            byte[] der = Base64.getDecoder().decode(vault.getPublicKey());
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Stored election public key is invalid", e);
        }
    }

    public boolean pollIsClosed() {
        return pollClosures.existsById(PollClosure.SINGLETON_ID);
    }

    @Transactional
    public void closePoll(String actor) {
        if (!pollIsClosed()) {
            pollClosures.save(new PollClosure(clock.instant(), actor));
            audit.record("POLL_CLOSED", actor, Map.of());
        }
    }

     @throws PollStillOpenException  
     @throws ShareException          
     @throws KeyCeremonyException     
     
    @Transactional(readOnly = true)
    public DecryptionResult decryptBallotBox(List<String> shareTexts, String actor) {

        if (!pollIsClosed()) {
            audit.record("DECRYPTION_REFUSED", actor, Map.of("reason", "POLL_OPEN"));
            throw new PollStillOpenException();
        }
        ElectionKeyVault vault = vaults.findById(ElectionKeyVault.SINGLETON_ID)
                .orElseThrow(() -> new KeyCeremonyException("The key ceremony has not been held yet."));

        PrivateKey electionKey;
        byte[] kek = null;
        try {
            kek = Shamir.combine(shareTexts, vault.getThreshold());
            electionKey = unlock(kek, vault.getLockedPrivateKey());
        } catch (ShareException e) {
            audit.record("DECRYPTION_REFUSED", actor,
                    Map.of("reason", "SHARES", "sharesOffered", String.valueOf(shareTexts.size())));
            throw e;
        } finally {
            if (kek != null) {
                Arrays.fill(kek, (byte) 0);
            }
        }

        List<String> plaintexts = new ArrayList<>();
        int undecryptable = 0;
        for (StoredBallot ballot : ballots.findAll()) {
            try {
                plaintexts.add(BallotEnvelope.decrypt(electionKey, ballot.getEnvelope()));
            } catch (IllegalArgumentException notOurFormatOrTampered) {
                undecryptable++;
            }
        }
        plaintexts.sort(null);
        audit.record("BALLOT_BOX_DECRYPTED", actor, Map.of(
                "sharesOffered", String.valueOf(shareTexts.size()),
                "decrypted", String.valueOf(plaintexts.size()),
                "undecryptable", String.valueOf(undecryptable)));
        return new DecryptionResult(plaintexts, undecryptable);
    }

    private static String lock(byte[] kek, byte[] privateKeyDer) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = gcm.doFinal(privateKeyDer);
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(out);
    }

    private static PrivateKey unlock(byte[] kek, String locked) {
        try {
            byte[] blob = Base64.getDecoder().decode(locked);
            Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
            gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(128, blob, 0, 12));
            byte[] der = gcm.doFinal(blob, 12, blob.length - 12);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException wrongKek) {
            throw new ShareException("The shares do not unlock the election key.");
        }
    }
}