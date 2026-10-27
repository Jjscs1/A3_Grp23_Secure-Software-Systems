package com.evp.results;

import com.evp.audit.AuditLogger;
import com.evp.audit.AuditService;
import com.evp.security.Ed25519Keys;
import com.evp.tally.PublishedTally;
import com.evp.tally.TallyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEC-18: signs and releases election results.
 *
 * Every release (first count, then any recount or correction) is a NEW signed payload:
 *
 *   {
 *     "schema":      "evp.results.v1",
 *     "electionId":  1,
 *     "sequence":    3,                       always goes up: 1, 2, 3 ...
 *     "issuedAt":    "2026-05-02T11:42:07Z",
 *     "keyId":       "a1b2c3d4e5f60718",      which key signed this
 *     "results":     [ {tallyId, contest, region, elected:[{id,name}], documentSha256} ],
 *     "auditHead":   {seq, hash}              pins the SEC-17 audit log at this moment
 *   }
 *
 * The sequence number and timestamp are INSIDE the signed payload, so they cannot be
 * changed without breaking the signature. documentSha256 ties each result to the full
 * SEC-16 tally document, which anyone can download and recount.
 */
@Service
public class ResultsPublisher {

    public static final String SCHEMA = "evp.results.v1";

    private final ResultsPublicationRepository publications;
    private final ResultsSigningConfig.ResultsSigningKey key;
    private final TallyService tallies;
    private final AuditService auditLog;
    private final AuditLogger audit;
    private final Clock clock;

    public ResultsPublisher(ResultsPublicationRepository publications, ResultsSigningConfig.ResultsSigningKey key,
                            TallyService tallies, AuditService auditLog, AuditLogger audit, Clock clock) {
        this.publications = publications;
        this.key = key;
        this.tallies = tallies;
        this.auditLog = auditLog;
        this.audit = audit;
        this.clock = clock;
    }

    public String publicKeyPem() {
        return Ed25519Keys.publicKeyPem(key.keyPair().getPublic());
    }

    public String keyId() {
        return ResultsSigning.keyId(publicKeyPem());
    }

    /** Signs the results of the given published tallies as the next version for this election. */
    @Transactional
    public JsonNode publish(long electionId, List<Long> tallyIds, String actor) {
        long sequence = publications.findTopByElectionIdOrderBySequenceDesc(electionId)
                .map(p -> p.getSequence() + 1).orElse(1L);

        ObjectNode payload = ResultsSigning.JSON.createObjectNode();
        payload.put("schema", SCHEMA);
        payload.put("electionId", electionId);
        payload.put("sequence", sequence);
        payload.put("issuedAt", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
        payload.put("keyId", keyId());
        ArrayNode results = payload.putArray("results");
        for (long tallyId : tallyIds) {
            results.add(summary(tallies.get(tallyId)));
        }
        AuditService.Head head = auditLog.head();
        payload.putObject("auditHead").put("seq", head.seq()).put("hash", head.hash());

        String signature = ResultsSigning.sign(key.keyPair().getPrivate(), payload);
        // saveAndFlush: if two admins publish at once, the UNIQUE (election, sequence) rejects one.
        publications.saveAndFlush(new ResultsPublication(electionId, sequence,
                new String(ResultsSigning.canonicalBytes(payload), StandardCharsets.UTF_8), signature, clock.instant()));

        Map<String, String> details = new HashMap<>();
        details.put("electionId", String.valueOf(electionId));
        details.put("sequence", String.valueOf(sequence));
        details.put("digest", ResultsSigning.digest(payload));
        audit.record("RESULTS_PUBLISHED", actor, details);
        return ResultsSigning.publication(payload, signature);
    }

    @Transactional(readOnly = true)
    public JsonNode latest(long electionId) {
        return toPublication(publications.findTopByElectionIdOrderBySequenceDesc(electionId)
                .orElseThrow(ResultsNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public JsonNode bySequence(long electionId, long sequence) {
        return toPublication(publications.findByElectionIdAndSequence(electionId, sequence)
                .orElseThrow(ResultsNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<Long> sequences(long electionId) {
        return publications.findByElectionIdOrderBySequenceAsc(electionId).stream()
                .map(ResultsPublication::getSequence).toList();
    }

    private static JsonNode toPublication(ResultsPublication p) {
        try {
            return ResultsSigning.publication(ResultsSigning.JSON.readTree(p.getPayload()), p.getSignature());
        } catch (Exception e) {
            throw new IllegalStateException("Stored payload is unreadable", e);
        }
    }

    /** Who won, plus a fingerprint of the full SEC-16 document that proves it. Integers and text only. */
    private static ObjectNode summary(PublishedTally tally) {
        JsonNode doc;
        try {
            doc = ResultsSigning.JSON.readTree(tally.getDocument());
        } catch (Exception e) {
            throw new IllegalStateException("Stored tally is unreadable", e);
        }
        Map<Integer, String> names = new HashMap<>();
        doc.path("candidates").forEach(c -> names.put(c.path("id").asInt(), c.path("name").asText()));

        ObjectNode out = ResultsSigning.JSON.createObjectNode();
        out.put("tallyId", tally.getId());
        out.put("contest", tally.getContest());
        out.put("region", tally.getRegion());
        ArrayNode elected = out.putArray("elected");
        JsonNode winners = "house".equals(tally.getContest())
                ? ResultsSigning.JSON.createArrayNode().add(doc.path("winner").asInt())
                : doc.path("elected");
        winners.forEach(w -> elected.addObject().put("id", w.asInt()).put("name", names.get(w.asInt())));
        out.put("documentSha256", ResultsSigning.sha256Hex(tally.getDocument().getBytes(StandardCharsets.UTF_8)));
        return out;
    }
}