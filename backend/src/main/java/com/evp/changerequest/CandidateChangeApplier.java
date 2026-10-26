package com.evp.changerequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * SEC-05: applies an approved CANDIDATE_DETAILS change.
 *
 * Payload: {"division":"MELBOURNE","name":"Jane Citizen","party":"Independent"}
 * Add "candidateId": 12 to edit an existing candidate instead of adding one.
 *
 * Names are checked against an allow-list here (SEC-06). The React front end still
 * renders them as text (SEC-07), so a name that slipped past this check could not run
 * as script.
 */
@Component
public class CandidateChangeApplier implements ChangeRequestApplier {

    private static final Pattern DIVISION = Pattern.compile("^[A-Za-z0-9 _\\-]{2,40}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L} .'\\-]{1,120}$");
    private static final Pattern PARTY = Pattern.compile("^[\\p{L}0-9 .'&()\\-]{0,120}$");

    record CandidatePayload(Long candidateId, String division, String name, String party) {
    }

    private final CandidateRepository candidates;
    private final ObjectMapper json;

    public CandidateChangeApplier(CandidateRepository candidates, ObjectMapper json) {
        this.candidates = candidates;
        this.json = json;
    }

    @Override
    public boolean supports(ChangeRequestType type) {
        return type == ChangeRequestType.CANDIDATE_DETAILS;
    }

    @Override
    public void validate(String payload) {
        parse(payload);
    }

    @Override
    public void apply(ChangeRequest request) {
        CandidatePayload p = parse(request.getPayload());
        if (p.candidateId() == null) {
            candidates.save(new Candidate(p.division(), p.name(), p.party()));
        } else {
            Candidate existing = candidates.findById(p.candidateId())
                    .orElseThrow(() -> new InvalidChangeRequestException("Candidate no longer exists"));
            existing.update(p.name(), p.party());
        }
    }

    private CandidatePayload parse(String payload) {
        CandidatePayload p;
        try {
            p = json.readValue(payload, CandidatePayload.class);
        } catch (JsonProcessingException e) {
            throw new InvalidChangeRequestException("Candidate payload is not valid JSON");
        }
        if (p.division() == null || !DIVISION.matcher(p.division()).matches()
                || p.name() == null || !NAME.matcher(p.name()).matches()
                || (p.party() != null && !PARTY.matcher(p.party()).matches())) {
            throw new InvalidChangeRequestException("Candidate payload has missing or invalid fields");
        }
        return p;
    }
}
