package com.evp.audit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    Optional<AuditEntry> findTopByOrderBySeqDesc();

    List<AuditEntry> findAllByOrderBySeqAsc();
}