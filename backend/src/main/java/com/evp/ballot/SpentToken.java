package com.evp.ballot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * SEC-11: one row per voting token already used. The UNIQUE constraint on serialHash is
 * what makes "vote exactly once" hold even when two submissions race: the database
 * accepts the first insert and rejects the second.
 *
 * Only the SHA-256 of the serial is stored. There is no link to a voter or to a ballot.
 *
 * The surrogate generated id is deliberate. With an assigned id, Spring Data's save()
 * would merge (update) an existing row instead of inserting, and a reused token would
 * slip through silently.
 */
@Entity
@Table(name = "spent_token",
        uniqueConstraints = @UniqueConstraint(name = "uq_spent_token_serial_hash", columnNames = "serial_hash"))
public class SpentToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_hash", nullable = false, length = 64)
    private String serialHash;

    protected SpentToken() {
        // for JPA
    }

    public SpentToken(String serialHash) {
        this.serialHash = serialHash;
    }

    public String getSerialHash() {
        return serialHash;
    }
}
