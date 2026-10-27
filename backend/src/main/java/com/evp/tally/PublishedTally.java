package com.evp.tally;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "published_tally")
public class PublishedTally {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String contest;

    @Column(nullable = false, length = 64)
    private String region;

    @Column(nullable = false)
    private Instant publishedAt;

    @Column(nullable = false, length = 255)
    private String publishedBy;

    private Long recountOf;

    @Column(nullable = false, length = 1_000_000)
    private String document;

    protected PublishedTally() {
        
    }

    PublishedTally(String contest, String region, Instant publishedAt, String publishedBy, Long recountOf,
                   String document) {
        this.contest = contest;
        this.region = region;
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
        this.recountOf = recountOf;
        this.document = document;
    }

    public Long getId() {
        return id;
    }

    public String getContest() {
        return contest;
    }

    public String getRegion() {
        return region;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Long getRecountOf() {
        return recountOf;
    }

    public String getDocument() {
        return document;
    }
}