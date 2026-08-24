package com.quietterminal.ledger.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.quietterminal.ledger.error.RecoveryCodeInvalidException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recovery_codes")
public class RecoveryCode {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String codeHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant usedAt;

    protected RecoveryCode() {
    }

    public RecoveryCode(UUID userId, String codeHash) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "User id cannot be null.");
        this.codeHash = Objects.requireNonNull(codeHash, "Code hash cannot be null.");
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed() {
        if (isUsed()) {
            throw new RecoveryCodeInvalidException("This recovery code has already been used.");
        }
        this.usedAt = Instant.now();
    }
}
