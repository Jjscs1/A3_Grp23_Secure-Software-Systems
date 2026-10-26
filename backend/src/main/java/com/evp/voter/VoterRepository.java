package com.evp.voter;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Parameterised queries only (SEC-06): no string-built SQL anywhere. */
public interface VoterRepository extends JpaRepository<Voter, UUID> {

    /**
     * SEC-11: row lock so two simultaneous token requests from the same voter are
     * serialised. The second one sees hasVoted = true and is refused.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voter v where v.oidcSubject = :subject")
    Optional<Voter> findBySubjectForUpdate(@Param("subject") String subject);

    /** SEC-08: exact match on the HMAC lookup key. */
    boolean existsByLookupKeyAndEnrolledTrue(String lookupKey);
}
