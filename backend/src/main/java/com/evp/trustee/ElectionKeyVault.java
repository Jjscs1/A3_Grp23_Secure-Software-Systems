package com.evp.trustee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "election_key_vault")
public class ElectionKeyVault {
    
    static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 1024)
    private String publicKey;

    @Column(nullable = false, length = 4096)
    private String lockedPrivateKey;

    @Column(nullable = false)
    private int threshold;

    @Column(nullable = false)
    private int trustees;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 255)
    private String createdBy;

    protected ElectionKeyVault() {
       
    }

    ElectionKeyVault(String publicKey, String lockedPrivateKey, int threshold, int trustees,
                     Instant createdAt, String createdBy) {
        this.id = SINGLETON_ID;
        this.publicKey = publicKey;
        this.lockedPrivateKey = lockedPrivateKey;
        this.threshold = threshold;
        this.trustees = trustees;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getLockedPrivateKey() {
        return lockedPrivateKey;
    }

    public int getThreshold() {
        return threshold;
    }

    public int getTrustees() {
        return trustees;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}