package com.evp.changerequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Minimal House of Representatives candidate, created or edited only through an approved
 * CANDIDATE_DETAILS change request. If a teammate already has a Candidate entity, point
 * CandidateChangeApplier at theirs and delete this one.
 */
@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String division;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String party;

    protected Candidate() {
        // for JPA
    }

    public Candidate(String division, String name, String party) {
        this.division = division;
        this.name = name;
        this.party = party;
    }

    void update(String name, String party) {
        this.name = name;
        this.party = party;
    }

    public Long getId() {
        return id;
    }

    public String getDivision() {
        return division;
    }

    public String getName() {
        return name;
    }

    public String getParty() {
        return party;
    }
}
