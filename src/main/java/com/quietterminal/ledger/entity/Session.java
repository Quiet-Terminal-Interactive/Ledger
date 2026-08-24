package com.quietterminal.ledger.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(updatable = false)
    private String userAgent;

    protected Session() {
    }

    public Session(UUID id, UUID userId, Instant createdAt, Instant expiresAt, String userAgent) {
        this.id = Objects.requireNonNull(id, "Id cannot be null.");
        this.userId = Objects.requireNonNull(userId, "User id cannot be null.");
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null.");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expires at cannot be null.");
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
