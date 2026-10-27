package com.evp.trustee;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public final class BallotEnvelope {

    public static final String ALGORITHM = "RSA-OAEP-256+A256GCM";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private BallotEnvelope() {
    }

    public static String encrypt(PublicKey electionKey, String ballot) {
        try {
            KeyGenerator aes = KeyGenerator.getInstance("AES");
            aes.init(256);
            SecretKey ballotKey = aes.generateKey();
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);

            Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
            gcm.init(Cipher.ENCRYPT_MODE, ballotKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = gcm.doFinal(ballot.getBytes(StandardCharsets.UTF_8));

            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.ENCRYPT_MODE, electionKey, OAEP_SHA256);
            byte[] wrappedKey = rsa.doFinal(ballotKey.getEncoded());

            Base64.Encoder b64 = Base64.getEncoder();
            return JSON.writeValueAsString(Map.of(
                    "alg", ALGORITHM,
                    "key", b64.encodeToString(wrappedKey),
                    "iv", b64.encodeToString(iv),
                    "ct", b64.encodeToString(ciphertext)));
        } catch (Exception e) {
            throw new IllegalStateException("Ballot encryption failed", e);
        }
    }

    @throws IllegalArgumentException
    public static String decrypt(PrivateKey electionKey, String envelope) {
        try {
            JsonNode node = JSON.readTree(envelope);
            if (!ALGORITHM.equals(node.path("alg").asText())) {
                throw new IllegalArgumentException("Not an " + ALGORITHM + " envelope");
            }
            Base64.Decoder b64 = Base64.getDecoder();
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.DECRYPT_MODE, electionKey, OAEP_SHA256);
            byte[] ballotKey = rsa.doFinal(b64.decode(node.path("key").asText()));

            Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
            gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(ballotKey, "AES"),
                    new GCMParameterSpec(128, b64.decode(node.path("iv").asText())));
            return new String(gcm.doFinal(b64.decode(node.path("ct").asText())), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Envelope could not be decrypted (wrong key or modified)", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Envelope is malformed", e);
        }
    }
}