package com.evp.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads election private keys from PKCS#8 PEM files named {@code <kid>.pem} in a directory that is
 * NOT in the repository (*.pem is git-ignored). Create one with:
 * {@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out ballot-keys/2026-1.pem}
 */
public final class KeyringLoader {
    private static final Pattern KID = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private KeyringLoader() { }

    public static ElectionKeyring fromDirectory(Path dir, String activeKid) throws IOException, GeneralSecurityException {
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IllegalStateException("ballot key directory is not configured or does not exist");
        }
        List<ElectionKey> keys = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".pem"))::iterator) {
                String name = f.getFileName().toString();
                String kid = name.substring(0, name.length() - 4);
                if (!KID.matcher(kid).matches()) {
                    throw new IllegalStateException("key file name is not a valid key id");
                }
                keys.add(fromPem(kid, Files.readString(f)));
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("no ballot keys found");
        }
        return new ElectionKeyring(keys, activeKid);
    }

    static ElectionKey fromPem(String kid, String pem) throws GeneralSecurityException {
        String b64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        PrivateKey priv = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) priv;
        RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        return new ElectionKey(kid, RsaOaepAesGcm.ID, priv, pub);
    }

    /** Throw-away keys for demos/tests ONLY: ballots sealed to them are lost on restart. */
    public static ElectionKeyring ephemeral(String kid, int bits) throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        KeyPair kp = g.generateKeyPair();
        return new ElectionKeyring(List.of(new ElectionKey(kid, RsaOaepAesGcm.ID, kp.getPrivate(), (RSAPublicKey) kp.getPublic())), kid);
    }
}
