package com.evp.dataprotection;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Data-encryption keys, read ONLY from the environment / a secret store, never from the repository.
 * <pre>
 *   EVP_DATA_KEYS   = k1:&lt;base64 32 bytes&gt;,k2:&lt;base64 32 bytes&gt;   (old keys stay so old rows can be read)
 *   EVP_DATA_ACTIVE = k2                                          (key used for new writes)
 *   EVP_INDEX_KEY   = &lt;base64 32 bytes&gt;                            (HMAC key for the email lookup index)
 * </pre>
 * Fails closed: missing, malformed, short, all-zero or obvious placeholder keys stop start-up.
 */
public final class DataKeys {
    private static final Pattern KID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final Map<String, SecretKey> keys;
    private final String active;
    private final SecretKey indexKey;

    private DataKeys(Map<String, SecretKey> keys, String active, SecretKey indexKey) {
        this.keys = keys;
        this.active = active;
        this.indexKey = indexKey;
    }

    /**
     * Production: {@link #fromEnv}. Tests only: with allowEphemeral=true and NO key variables set, random in-memory keys
     * are generated (data does not survive a restart). If any key variable is set, it must be valid, flag or not.
     */
    public static DataKeys fromEnvOrEphemeral(Map<String, String> env, boolean allowEphemeral) {
        boolean anySet = env.get("EVP_DATA_KEYS") != null || env.get("EVP_DATA_ACTIVE") != null || env.get("EVP_INDEX_KEY") != null;
        if (!allowEphemeral || anySet) {
            return fromEnv(env);
        }
        java.security.SecureRandom r = new java.security.SecureRandom();
        byte[] a = new byte[32];
        byte[] b = new byte[32];
        r.nextBytes(a);
        r.nextBytes(b);
        Map<String, SecretKey> m = new LinkedHashMap<>();
        m.put("test", new SecretKeySpec(a, "AES"));
        return new DataKeys(m, "test", new SecretKeySpec(b, "AES"));
    }

    public static DataKeys fromEnv(Map<String, String> env) {
        String list = need(env, "EVP_DATA_KEYS");
        String active = need(env, "EVP_DATA_ACTIVE");
        Map<String, SecretKey> parsed = new LinkedHashMap<>();
        for (String item : list.split(",")) {
            int i = item.indexOf(':');
            if (i < 1) {
                throw new IllegalStateException("EVP_DATA_KEYS entry must be kid:base64");
            }
            String kid = item.substring(0, i).trim();
            if (!KID.matcher(kid).matches()) {
                throw new IllegalStateException("bad key id");
            }
            if (parsed.put(kid, key("EVP_DATA_KEYS[" + kid + "]", item.substring(i + 1).trim())) != null) {
                throw new IllegalStateException("duplicate key id");
            }
        }
        if (!parsed.containsKey(active)) {
            throw new IllegalStateException("EVP_DATA_ACTIVE does not name a configured key");
        }
        SecretKey idx = key("EVP_INDEX_KEY", need(env, "EVP_INDEX_KEY"));
        for (SecretKey k : parsed.values()) {
            if (java.util.Arrays.equals(k.getEncoded(), idx.getEncoded())) {
                throw new IllegalStateException("index key must differ from data keys");
            }
        }
        return new DataKeys(parsed, active, idx);
    }

    private static String need(Map<String, String> env, String name) {
        String v = env.get(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return v;
    }

    private static SecretKey key(String what, String b64) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(what + " is not valid base64");
        }
        if (raw.length != 32) {
            throw new IllegalStateException(what + " must be exactly 32 bytes");
        }
        boolean allSame = true;
        for (byte b : raw) {
            allSame &= b == raw[0];
        }
        if (allSame) {
            throw new IllegalStateException(what + " looks like a placeholder");
        }
        return new SecretKeySpec(raw, "AES");
    }

    public String activeId() {
        return active;
    }

    public SecretKey active() {
        return keys.get(active);
    }

    public SecretKey get(String kid) {
        return keys.get(kid);
    }

    public SecretKey indexKey() {
        return indexKey;
    }

    @Override
    public String toString() {
        return "DataKeys[active=" + active + ", count=" + keys.size() + "]";
    }
}
