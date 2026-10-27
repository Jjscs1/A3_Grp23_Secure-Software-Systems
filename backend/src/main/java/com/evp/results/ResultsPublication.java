package com.evp.results;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * SEC-18: one signed release of an election's results. Never edited: a correction is a NEW
 * row with the next sequence number, so every earlier version stays visible.
 * UNIQUE (election, sequence): two publications can never share a sequence number.
 */
@Entity
@Immutable
@Table(name = "results_publication",
        uniqueConstraints = @UniqueConstraint(name = "uq_results_election_sequence",
                columnNames = {"election_id", "sequence"}))
public class ResultsPublication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private long electionId;

    @Column(nullable = false)
    private long sequence;

    /** The signed payload exactly as signed (canonical JSON). */
    @Column(nullable = false, length = 100_000)
    private String payload;

    @Column(nullable = false, length = 120)
    private String signature;

    @Column(nullable = false)
    private Instant publishedAt;

    protected ResultsPublication() {
        // for JPA
    }

    ResultsPublication(long electionId, long sequence, String payload, String signature, Instant publishedAt) {
        this.electionId = electionId;
        this.sequence = sequence;
        this.payload = payload;
        this.signature = signature;
        this.publishedAt = publishedAt;
    }

    public long getElectionId() {
        return electionId;
    }

    public long getSequence() {
        return sequence;
    }

    public String getPayload() {
        return payload;
    }

    public String getSignature() {
        return signature;
    }
}