package com.evp.trustee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "poll_closure")
public class PollClosure {

    static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private Instant closedAt;

    @Column(nullable = false, length = 255)
    private String closedBy;

    protected PollClosure() {
        // for JPA
    }

    PollClosure(Instant closedAt, String closedBy) {
        this.id = SINGLETON_ID;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }
}