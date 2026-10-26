package com.evp.ballot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpentTokenRepository extends JpaRepository<SpentToken, Long> {

    long countBySerialHash(String serialHash);
}
