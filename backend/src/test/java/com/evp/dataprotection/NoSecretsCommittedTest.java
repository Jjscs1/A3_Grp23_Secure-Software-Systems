package com.evp.dataprotection;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Fails the build if a secret is committed under backend/ (run in CI with the unit tests). */
class NoSecretsCommittedTest {
    static final List<Pattern> BAD = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("(?i)(password|passwd|secret|api[_-]?key|token)\\s*[=:]\\s*(?!\\$\\{)[\"']?[A-Za-z0-9+/=_-]{8,}"),
            Pattern.compile("[A-Za-z0-9+/]{43}="));

    static List<String> scan(Path root) throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                String n = p.getFileName().toString();
                if (!(n.endsWith(".java") || n.endsWith(".properties") || n.endsWith(".yml") || n.endsWith(".yaml")
                        || n.endsWith(".sql") || n.endsWith(".xml") || n.endsWith(".json") || n.endsWith(".ts") || n.endsWith(".tsx"))) {
                    continue;
                }
                if (p.toString().contains("/src/test/")) {
                    continue; // tests contain deliberately fake values
                }
                String text = Files.readString(p);
                for (Pattern b : BAD) {
                    if (b.matcher(text).find()) {
                        hits.add(p + " matches " + b.pattern());
                    }
                }
            }
        }
        return hits;
    }

    @Test
    void mainSourcesContainNoSecrets() throws IOException {
        assertEquals(List.of(), scan(Path.of("src/main")));
    }

    @Test
    void scannerActuallyCatchesSecrets(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src/main"));
        Files.writeString(dir.resolve("src/main/a.properties"), "db.password=hunter22hunter\n");
        Files.writeString(dir.resolve("src/main/b.java"), "String k = \"-----BEGIN RSA PRIVATE KEY-----\";");
        Files.writeString(dir.resolve("src/main/c.properties"), "k=" + "A".repeat(43) + "=\n");
        Files.writeString(dir.resolve("src/main/ok.properties"), "db.password=${EVP_DB_PASSWORD}\n");
        assertEquals(3, scan(dir).size());
    }

    @Test
    void propertiesUseEnvironmentPlaceholdersForEverySecret() throws IOException {
        String p = Files.readString(Path.of("src/main/resources/application-tls.properties"));
        assertTrue(p.contains("server.ssl.key-store-password=${EVP_TLS_KEYSTORE_PASSWORD}"));
        assertTrue(p.contains("spring.datasource.password=${EVP_DB_PASSWORD}"));
    }

    @Test
    void gitignoreBlocksSecretFilesAndKeepsTheExample() throws IOException {
        String g = Files.readString(Path.of(".gitignore"));
        for (String must : new String[] {".env", "*.pem", "*.p12", "*.jks", "target/", "!.env.example"}) {
            assertTrue(g.contains(must), must);
        }
        String ex = Files.readString(Path.of(".env.example"));
        assertTrue(ex.contains("CHANGE_ME"));
        assertFalse(Pattern.compile("[A-Za-z0-9+/]{43}=").matcher(ex).find());
    }

    @Test
    void tlsProfileIsTls13OnlyWithAeadSuites() throws IOException {
        String p = Files.readString(Path.of("src/main/resources/application-tls.properties"));
        assertTrue(p.contains("server.ssl.enabled-protocols=TLSv1.3"));
        assertFalse(p.contains("TLSv1.2"));
        assertFalse(p.contains("TLSv1.1"));
        for (String suite : p.lines().filter(l -> l.startsWith("server.ssl.ciphers=")).findFirst().orElseThrow().substring(19).split(",")) {
            assertTrue(suite.startsWith("TLS_AES_") || suite.startsWith("TLS_CHACHA20_"), suite);
        }
        assertTrue(p.contains("cookie.secure=true"));
    }
}
