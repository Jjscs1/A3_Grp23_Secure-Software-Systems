package com.evp.changerequest;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findByDivisionOrderByNameAsc(String division);
}
