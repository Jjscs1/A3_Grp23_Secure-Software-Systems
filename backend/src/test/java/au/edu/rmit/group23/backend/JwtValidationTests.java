package au.edu.rmit.group23.backend;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtValidationTests {

    private static final String ISSUER =
        "http://localhost:8081/realms/evp";

    private static final String AUDIENCE = "evp-backend";

    private static final RSAKey TRUSTED_KEY;
    private static final RSAKey WRONG_KEY;
    private static final HttpServer KEY_SERVER;

    static {
        try {
            TRUSTED_KEY = new RSAKeyGenerator(2048)
                .keyID("test-key")
                .generate();

            WRONG_KEY = new RSAKeyGenerator(2048)
                .keyID("test-key")
                .generate();

            byte[] publicKeys = new JWKSet(TRUSTED_KEY.toPublicJWK())
                .toString()
                .getBytes(StandardCharsets.UTF_8);

            KEY_SERVER = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

            KEY_SERVER.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders()
                    .set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, publicKeys.length);

                try (var output = exchange.getResponseBody()) {
                    output.write(publicKeys);
                }
            });

            KEY_SERVER.start();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void useLocalTestKeys(DynamicPropertyRegistry registry) {
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:"
                + KEY_SERVER.getAddress().getPort() + "/jwks"
        );
    }

    @AfterAll
    static void stopKeyServer() {
        KEY_SERVER.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    private String createToken(
            RSAKey signingKey,
            String issuer,
            String audience,
            Instant expiresAt) throws Exception {

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("jwt-test-user")
            .claim("preferred_username", "voter-test")
            .issueTime(Date.from(Instant.now().minusSeconds(600)))
            .notBeforeTime(Date.from(Instant.now().minusSeconds(600)))
            .expirationTime(Date.from(expiresAt))
            .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(JOSEObjectType.JWT)
            .keyID("test-key")
            .build();

        SignedJWT token = new SignedJWT(header, claims);
        token.sign(new RSASSASigner(signingKey));

        return token.serialize();
    }

    @Test
    void acceptsCorrectlySignedToken() throws Exception {
        String token = createToken(
            TRUSTED_KEY, ISSUER, AUDIENCE,
            Instant.now().plusSeconds(300));

        mvc.perform(get("/api/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subject").value("jwt-test-user"))
            .andExpect(jsonPath("$.username").value("voter-test"));
    }

    @Test
    void rejectsWrongSignature() throws Exception {
        String token = createToken(
            WRONG_KEY, ISSUER, AUDIENCE,
            Instant.now().plusSeconds(300));

        mvc.perform(get("/api/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = createToken(
            TRUSTED_KEY, ISSUER, AUDIENCE,
            Instant.now().minusSeconds(300));

        mvc.perform(get("/api/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        String token = createToken(
            TRUSTED_KEY, "https://wrong-issuer.example", AUDIENCE,
            Instant.now().plusSeconds(300));

        mvc.perform(get("/api/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        String token = createToken(
            TRUSTED_KEY, ISSUER, "another-api",
            Instant.now().plusSeconds(300));

        mvc.perform(get("/api/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }
}