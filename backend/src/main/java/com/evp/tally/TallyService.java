package com.evp.tally;
import com.evp.audit.AuditLogger;
import com.evp.changerequest.ChangeRequest;
import com.evp.changerequest.ChangeRequestRepository;
import com.evp.changerequest.ChangeRequestStatus;
import com.evp.changerequest.ChangeRequestType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TallyService {

    private final PublishedTallyRepository tallies;
    private final ChangeRequestRepository changeRequests;
    private final AuditLogger audit;
    private final Clock clock;

    public TallyService(PublishedTallyRepository tallies, ChangeRequestRepository changeRequests,
                        AuditLogger audit, Clock clock) {
        this.tallies = tallies;
        this.changeRequests = changeRequests;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PublishedTally publishHouse(String region, List<TallyDocument.Candidate> candidates,
                                       List<List<Integer>> ballots, List<String> receipts, long seed, String actor) {
        validate(candidates, ballots);
        ObjectNode doc = TallyDocument.house(region, candidates, ballots, receipts, seed);
        return save(doc, null, actor);
    }

    @Transactional
    public PublishedTally publishSenate(String region, List<TallyDocument.Candidate> candidates,
                                        List<List<Integer>> ballots, int vacancies, List<String> receipts,
                                        long seed, String actor) {
        validate(candidates, ballots);
        if (vacancies < 1 || vacancies >= candidates.size()) {
            throw new IllegalArgumentException("Vacancies must be at least 1 and fewer than the candidates");
        }
        ObjectNode doc = TallyDocument.senate(region, candidates, ballots, vacancies, receipts,
                List.of(), List.of(), seed);
        return save(doc, null, actor);
    }
 
    @Transactional
    public PublishedTally recount(long tallyId, String actor) {
        PublishedTally original = tallies.findById(tallyId).orElseThrow(TallyNotFoundException::new);
        JsonNode doc = read(original);
        if (!"senate".equals(doc.path("contest").asText())) {
            throw new TallyException("Court-ordered recount exclusions apply to Senate counts only.");
        }

        List<TallyDocument.Candidate> candidates = TallyDocument.candidates(doc);
        Set<Integer> stood = new HashSet<>();
        candidates.forEach(c -> stood.add(c.id()));

        Set<Integer> excluded = new TreeSet<>();
        List<Map<String, Object>> orders = new ArrayList<>();
        for (ChangeRequest cr : changeRequests.findByTypeAndStatus(
                ChangeRequestType.RECOUNT_EXCLUSION, ChangeRequestStatus.APPLIED)) {
            JsonNode payload = parse(cr.getPayload());
            int candidateId = payload.path("candidateId").asInt(-1);
            if (original.getRegion().equalsIgnoreCase(payload.path("division").asText())
                    && stood.contains(candidateId)) {
                excluded.add(candidateId);
                Map<String, Object> order = new LinkedHashMap<>();
                order.put("candidateId", candidateId);
                order.put("courtReference", cr.getCourtOrderReference());
                order.put("changeRequestId", cr.getId());
                order.put("proposedBy", cr.getProposer());
                order.put("approvedBy", cr.approverNames());
                order.put("approvedAt", String.valueOf(cr.getDecidedAt()));
                orders.add(order);
            }
        }
        if (excluded.isEmpty()) {
            throw new TallyException("No approved recount exclusion for " + original.getRegion()
                    + ". Exclusions need a court order and two approvals (SEC-05).");
        }

        ObjectNode again = TallyDocument.senate(original.getRegion(), candidates, TallyDocument.ballots(doc),
                doc.path("countInputs").path("vacancies").asInt(), TallyDocument.strings(doc.path("ballotBox")
                        .path("receipts")), List.copyOf(excluded), orders,
                doc.path("countInputs").path("tiebreakSeed").asLong());
        return save(again, original.getId(), actor);
    }

    @Transactional(readOnly = true)
    public PublishedTally get(long id) {
        return tallies.findById(id).orElseThrow(TallyNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<PublishedTally> all() {
        return tallies.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<String> verify(long id) {
        return TallyVerifier.problems(read(get(id)));
    }

    static JsonNode read(PublishedTally tally) {
        return parse(tally.getDocument());
    }

    private PublishedTally save(ObjectNode doc, Long recountOf, String actor) {
        PublishedTally saved = tallies.save(new PublishedTally(doc.path("contest").asText(),
                doc.path("region").asText(), clock.instant(), actor, recountOf, doc.toString()));
        Map<String, String> details = new LinkedHashMap<>();
        details.put("tallyId", String.valueOf(saved.getId()));
        details.put("contest", saved.getContest());
        details.put("region", saved.getRegion());
        if (recountOf != null) {
            details.put("recountOf", String.valueOf(recountOf));
        }
        audit.record(recountOf == null ? "TALLY_PUBLISHED" : "RECOUNT_PUBLISHED", actor, details);
        return saved;
    }

    private static JsonNode parse(String json) {
        try {
            return TallyDocument.JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Stored JSON is unreadable", e);
        }
    }

    private static void validate(List<TallyDocument.Candidate> candidates, List<List<Integer>> ballots) {
        Set<Integer> ids = new HashSet<>();
        for (TallyDocument.Candidate c : candidates) {
            if (!ids.add(c.id())) {
                throw new IllegalArgumentException("Duplicate candidate id " + c.id());
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("No candidates");
        }
        for (List<Integer> ballot : ballots) {
            if (ballot.isEmpty() || !ids.containsAll(ballot) || new HashSet<>(ballot).size() != ballot.size()) {
                throw new IllegalArgumentException("Informal ballot in the count");
            }
        }
    }
}