package com.evp.token;

import com.evp.audit.AuditLogger;
import com.evp.eligibility.EligibilityService;
import com.evp.eligibility.IneligibilityReason;
import com.evp.eligibility.IneligibleVoterException;
import com.evp.voter.Voter;
import com.evp.voter.VoterRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEC-10 and SEC-11 together, in one transaction:
 *   lock the voter row, check eligibility, set has-voted, sign the blinded token.
 *
 * If anything fails, the whole transaction rolls back and has-voted stays false. Because
 * the row is locked, two simultaneous requests from one voter cannot both get a token.
 * The audit record names the voter but contains nothing about the token, which the
 * server cannot see anyway.
 *
 * Design note for the report: has-voted is set when the token is issued, not when the
 * ballot arrives. A voter who gets a token and then abandons the session cannot get a
 * second one online, and would need to vote on paper at a polling place.
 */
@Service
public class TokenIssuanceService {

    private final VoterRepository voters;
    private final EligibilityService eligibility;
    private final BlindSigner signer;
    private final AuditLogger audit;

    public TokenIssuanceService(VoterRepository voters, EligibilityService eligibility,
                                BlindSigner signer, AuditLogger audit) {
        this.voters = voters;
        this.eligibility = eligibility;
        this.signer = signer;
        this.audit = audit;
    }

    @Transactional
    public byte[] issue(String voterSubject, String requestedDivision, byte[] blindedMessage) {
        Voter voter = voters.findBySubjectForUpdate(voterSubject)
                .orElseThrow(() -> refuse(voterSubject, IneligibilityReason.NOT_ENROLLED));
        try {
            eligibility.check(voter, requestedDivision);
        } catch (IneligibleVoterException e) {
            throw refuse(voterSubject, e.getReason());
        }

        voter.markVoted();
        byte[] blindSignature = signer.blindSign(blindedMessage);

        audit.record("BALLOT_TOKEN_ISSUED", voterSubject, Map.of("division", requestedDivision));
        return blindSignature;
    }

    private IneligibleVoterException refuse(String voterSubject, IneligibilityReason reason) {
        audit.record("BALLOT_TOKEN_REFUSED", voterSubject, Map.of("reason", reason.name()));
        return new IneligibleVoterException(reason);
    }
}
