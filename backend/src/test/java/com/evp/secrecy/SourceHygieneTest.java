package com.evp.secrecy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Build-time guards: fail if anyone adds code that could log or store network identity or ballot content. */
class SourceHygieneTest {

    private static List<Path> mainSources() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"), "src", "main", "java");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void noRequestPayloadLoggingFilterAnywhere() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : mainSources()) {
            String src = Files.readString(p);
            if (src.contains("CommonsRequestLoggingFilter") || src.contains("setIncludePayload")
                    || src.contains("AbstractRequestLoggingFilter")) {
                bad.add(p.toString());
            }
        }
        assertEquals(List.of(), bad);
    }

    @Test
    void ballotCodeNeverReadsTheClientAddress() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : mainSources()) {
            String n = p.toString();
            if (n.contains("/ballot") || n.contains("/secrecy/") || n.contains("/crypto/") || n.contains("/tally/")) {
                String src = Files.readString(p);
                if (src.contains("getRemoteAddr") || src.contains("getRemoteHost") || src.contains("X-Forwarded-For")
                        || src.contains("getHeader(\"User-Agent\")")) {
                    if (!n.endsWith("StrippedRequest.java")) {
                        bad.add(n);
                    }
                }
            }
        }
        assertEquals(List.of(), bad);
    }

    @Test
    void accessLogFilterNeverTouchesBodyOrHeaders() throws IOException {
        Path p = Paths.get(System.getProperty("user.dir"), "src", "main", "java", "com", "evp", "secrecy", "PrivacyAccessLogFilter.java");
        if (!Files.exists(p)) {
            return;
        }
        String src = Files.readString(p);
        for (String forbidden : List.of("getInputStream", "getReader", "getParameter", "getHeader", "getQueryString",
                "getRemoteAddr", "getUserPrincipal", "getMessage")) {
            assertFalse(src.contains(forbidden), forbidden);
        }
    }

    @Test
    void ballotTableDdlHasNoIdentityReference() {
        String ddl = BallotSchema.CREATE_TABLE.toLowerCase();
        assertFalse(ddl.contains("references"));
        assertFalse(ddl.contains("foreign"));
        assertFalse(ddl.matches(".*(voter_id|user_id|ip_addr|session).*"));
        assertTrue(ddl.contains("cast_hour"));
        assertFalse(ddl.contains("timestamp"));
    }

    @Test
    void propertiesDisableContainerAccessLogAndForwardedHeaders() throws IOException {
        Path p = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "secrecy.properties");
        if (!Files.exists(p)) {
            return;
        }
        String props = Files.readString(p);
        assertTrue(props.contains("server.tomcat.accesslog.enabled=false"));
        assertTrue(props.contains("server.forward-headers-strategy=none"));
        assertTrue(props.contains("CommonsRequestLoggingFilter=OFF"));
        assertFalse(props.matches("(?s).*[Pp]assword\\s*=\\s*\\S+.*"), "no password may be written into the repository");
    }
}
