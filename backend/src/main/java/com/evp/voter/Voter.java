package com.evp.voter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Electoral roll entry, holding the fields SEC-08, SEC-10 and SEC-11 need.
 *
 * Integration notes for teammates:
 *  - Whoever builds registration and address updates must set {@code lookupKey} with
 *    EnrolmentLookupKey.compute(name, dob, postcode) on create, and recompute it when the
 *    name or postcode changes, otherwise SEC-08 will report the voter as not enrolled.
 *  - SEC-19 can encrypt dateOfBirth at rest with a JPA AttributeConverter without
 *    changing this class's callers.
 *  - SEC-13: this table lives on the identity side. Nothing here references a ballot.
 */
@Entity
@Table(name = "voter", indexes = @Index(name = "ix_voter_lookup_key", columnList = "lookup_key"))
public class Voter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** OIDC subject from the Digital ID provider (SEC-01). */
    @Column(unique = true, length = 255)
    private String oidcSubject;

    /** SEC-08 exact match key: HMAC of normalised name, date of birth and postcode. */
    @Column(name = "lookup_key", nullable = false, length = 64)
    private String lookupKey;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private boolean citizen;

    @Column(nullable = false)
    private boolean enrolled;

    @Column(nullable = false, length = 40)
    private String division;

    /** Set in the same transaction that issues the voting token (SEC-11). */
    @Column(nullable = false)
    private boolean hasVoted;

    @Version
    private long version;

    protected Voter() {
        // for JPA
    }

    public static Voter enrol(String oidcSubject, String lookupKey, LocalDate dateOfBirth,
                              boolean citizen, String division) {
        Voter v = new Voter();
        v.oidcSubject = oidcSubject;
        v.lookupKey = lookupKey;
        v.dateOfBirth = dateOfBirth;
        v.citizen = citizen;
        v.enrolled = true;
        v.division = division;
        v.hasVoted = false;
        return v;
    }

    public void markVoted() {
        this.hasVoted = true;
    }

    public void removeFromRoll() {
        this.enrolled = false;
    }

    public UUID getId() {
        return id;
    }

    public String getOidcSubject() {
        return oidcSubject;
    }

    public String getLookupKey() {
        return lookupKey;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public boolean isCitizen() {
        return citizen;
    }

    public boolean isEnrolled() {
        return enrolled;
    }

    public String getDivision() {
        return division;
    }

    public boolean hasVoted() {
        return hasVoted;
    }
}
