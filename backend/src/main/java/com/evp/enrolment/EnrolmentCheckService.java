package com.evp.enrolment;

import com.evp.voter.VoterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SEC-08: public "am I enrolled?" check that cannot be used to harvest the roll.
 *
 *  - Exact match only, via the HMAC lookup key, so partial matches look like "not found".
 *  - One response shape, {"enrolled": true|false}, with HTTP 200 in both cases.
 *  - Every call is padded to the same minimum duration, so timing does not leak.
 *
 * Rate limiting of this endpoint is SEC-09 (Identity and access member).
 * Deliberately not @Transactional: the padding sleep must not hold a database connection.
 */
@Service
public class EnrolmentCheckService {

    private static final Logger log = LoggerFactory.getLogger(EnrolmentCheckService.class);

    private final VoterRepository voters;
    private final EnrolmentLookupKey lookupKey;
    private final ResponseTimePadder padder;

    public EnrolmentCheckService(VoterRepository voters, EnrolmentLookupKey lookupKey, ResponseTimePadder padder) {
        this.voters = voters;
        this.lookupKey = lookupKey;
        this.padder = padder;
    }

    public EnrolmentCheckResponse check(EnrolmentCheckRequest request) {
        long start = padder.start();
        boolean enrolled = false;
        try {
            String key = lookupKey.compute(request.fullName(), request.dateOfBirth(), request.postcode());
            enrolled = voters.existsByLookupKeyAndEnrolledTrue(key);
        } finally {
            if (padder.padFrom(start)) {
                log.warn("Enrolment check took longer than the {} minimum; response timing may leak",
                        padder.getMinimum());
            }
        }
        return new EnrolmentCheckResponse(enrolled);
    }
}
