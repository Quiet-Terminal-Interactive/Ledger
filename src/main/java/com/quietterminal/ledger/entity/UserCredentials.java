package com.quietterminal.ledger.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_credentials", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class UserCredentials {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    protected UserCredentials() {
    }

    public UserCredentials(UUID userId, String username, String passwordHash) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "User id cannot be null.");
        this.username = Objects.requireNonNull(username, "Username cannot be null.");
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash cannot be null.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash cannot be null.");
    }
}
