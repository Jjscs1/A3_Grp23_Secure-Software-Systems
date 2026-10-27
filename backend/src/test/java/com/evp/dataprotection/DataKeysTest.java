package com.evp.dataprotection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataKeysTest {
    static Map<String, String> good() {
        Map<String, String> m = new HashMap<>();
        m.put("EVP_DATA_KEYS", "k1:" + FieldCipherTest.b64());
        m.put("EVP_DATA_ACTIVE", "k1");
        m.put("EVP_INDEX_KEY", FieldCipherTest.b64());
        return m;
    }

    @Test
    void startsWithGoodConfig() {
        assertEquals("k1", DataKeys.fromEnv(good()).activeId());
    }

    @Test
    void failsClosedWhenAnythingIsMissing() {
        for (String name : new String[] {"EVP_DATA_KEYS", "EVP_DATA_ACTIVE", "EVP_INDEX_KEY"}) {
            Map<String, String> m = good();
            m.remove(name);
            assertThrows(IllegalStateException.class, () -> DataKeys.fromEnv(m), name);
            m.put(name, "  ");
            assertThrows(IllegalStateException.class, () -> DataKeys.fromEnv(m), name);
        }
    }

    @Test
    void rejectsWeakOrMalformedKeys() {
        String zeros = Base64.getEncoder().encodeToString(new byte[32]);
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);
        for (String bad : new String[] {"k1:" + zeros, "k1:" + short16, "k1:not base64!!", "k1", ":abc"}) {
            Map<String, String> m = good();
            m.put("EVP_DATA_KEYS", bad);
            assertThrows(IllegalStateException.class, () -> DataKeys.fromEnv(m), bad);
        }
    }

    @Test
    void rejectsUnknownActiveDuplicateIdsAndSharedIndexKey() {
        Map<String, String> m = good();
        m.put("EVP_DATA_ACTIVE", "k9");
        assertThrows(IllegalStateException.class, () -> DataKeys.fromEnv(m));
        Map<String, String> d = good();
        String k = FieldCipherTest.b64();
        d.put("EVP_DATA_KEYS", "k1:" + k + ",k1:" + FieldCipherTest.b64());
        assertThrows(IllegalStateException.class, () -> DataKeys.fromEnv(d));
        Map<String, String> s = good();
        s.put("EVP_DATA_KEYS", "k1:" + k);
        s.put("EVP_INDEX_KEY", k);
        assertThrows(IllegalStateException.class, () -> DataKeys.fromEnv(s));
    }

    @Test
    void ephemeralKeysOnlyWhenAllowedAndNothingConfigured() {
        assertThrows(IllegalStateException.class, () -> DataKeys.fromEnvOrEphemeral(Map.of(), false));
        DataKeys k = DataKeys.fromEnvOrEphemeral(Map.of(), true);
        FieldCipher c = new FieldCipher(k);
        assertEquals("x", c.decrypt(c.encrypt("x", "ctx"), "ctx"));
        assertNotEquals(k.indexKey().getEncoded().length, 0);
        // a half-configured environment is still an error, even with the flag
        assertThrows(IllegalStateException.class, () -> DataKeys.fromEnvOrEphemeral(Map.of("EVP_DATA_ACTIVE", "k1"), true));
        // two ephemeral sets differ
        assertFalse(java.util.Arrays.equals(k.active().getEncoded(), DataKeys.fromEnvOrEphemeral(Map.of(), true).active().getEncoded()));
    }
}
