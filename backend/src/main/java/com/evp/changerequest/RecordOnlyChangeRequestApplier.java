package com.evp.changerequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SEC-05: for change types whose effect lives in another member's subsystem. The
 * approved request is the record of authority, and the owning subsystem reads it:
 *  - RECOUNT_EXCLUSION: SEC-16's recount reads APPLIED exclusions from
 *    ChangeRequestRepository.findByTypeAndStatus(RECOUNT_EXCLUSION, APPLIED).
 *  - GROUP_ORDERING and BULK_ENROLMENT_CHANGE: the ballot and enrolment owners read
 *    APPLIED requests the same way, or replace this class with a real applier.
 */
@Component
public class RecordOnlyChangeRequestApplier implements ChangeRequestApplier {

    private static final Logger log = LoggerFactory.getLogger(RecordOnlyChangeRequestApplier.class);

    @Override
    public boolean supports(ChangeRequestType type) {
        return type != ChangeRequestType.CANDIDATE_DETAILS;
    }

    @Override
    public void apply(ChangeRequest request) {
        log.info("Change request {} ({}) approved; recorded for the owning subsystem", request.getId(), request.getType());
    }
}
