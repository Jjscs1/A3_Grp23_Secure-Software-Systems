package com.evp.ballot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Placeholder ballot storage used by JpaBallotSink until SEC-12/13/14 replace it.
 * Random UUID key, no voter reference, receipt time truncated to the hour.
 */
@Entity
@Table(name = "stored_ballot")
public class StoredBallot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 65536)
    private String envelope;

    @Column(nullable = false)
    private Instant receivedHour;

    protected StoredBallot() {
        // for JPA
    }

    public StoredBallot(String envelope, Instant now) {
        this.envelope = envelope;
        this.receivedHour = now.truncatedTo(ChronoUnit.HOURS);
    }

    public UUID getId() {
        return id;
    }

    public String getEnvelope() {
        return envelope;
    }

    public Instant getReceivedHour() {
        return receivedHour;
    }
}
