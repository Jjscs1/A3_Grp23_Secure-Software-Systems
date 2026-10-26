package com.evp.ballot;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredBallotRepository extends JpaRepository<StoredBallot, UUID> {
}
