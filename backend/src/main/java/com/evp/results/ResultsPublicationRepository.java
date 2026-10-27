package com.evp.results;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultsPublicationRepository extends JpaRepository<ResultsPublication, Long> {

    Optional<ResultsPublication> findTopByElectionIdOrderBySequenceDesc(long electionId);

    Optional<ResultsPublication> findByElectionIdAndSequence(long electionId, long sequence);

    List<ResultsPublication> findByElectionIdOrderBySequenceAsc(long electionId);
}