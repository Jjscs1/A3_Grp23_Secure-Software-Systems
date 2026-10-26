package com.evp.changerequest;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {

    List<ChangeRequest> findByStatusOrderByCreatedAtAsc(ChangeRequestStatus status);

    /**
     * For SEC-16 (tally owner): the recount uses only exclusions that went through dual
     * control, i.e. findByTypeAndStatus(RECOUNT_EXCLUSION, APPLIED).
     */
    List<ChangeRequest> findByTypeAndStatus(ChangeRequestType type, ChangeRequestStatus status);
}
