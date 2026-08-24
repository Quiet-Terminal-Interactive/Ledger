package com.quietterminal.ledger.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.quietterminal.ledger.enums.BudgetEntryType;
import com.quietterminal.ledger.error.BudgetEntryInvalidException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "budget_entries")
public class BudgetEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BudgetEntryType type;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BudgetEntry() {
    }

    public BudgetEntry(String name, String description, BigDecimal amount, BudgetEntryType type, User createdBy) {
        Objects.requireNonNull(createdBy, "Creator cannot be null.");
        Objects.requireNonNull(type, "Type cannot be null.");
        validateName(name);
        validateAmount(amount);

        this.uuid = UUID.randomUUID();
        this.name = name;
        this.description = description == null ? "" : description;
        this.amount = amount;
        this.type = type;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BudgetEntryInvalidException("Name cannot be blank.");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BudgetEntryInvalidException("Amount cannot be null.");
        }
        if (amount.signum() < 0) {
            throw new BudgetEntryInvalidException("Amount cannot be negative.");
        }
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        validateName(name);
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        validateAmount(amount);
        this.amount = amount;
        this.updatedAt = Instant.now();
    }

    public BudgetEntryType getType() {
        return type;
    }

    public void setType(BudgetEntryType type) {
        this.type = Objects.requireNonNull(type, "Type cannot be null.");
        this.updatedAt = Instant.now();
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BudgetEntry other)) {
            return false;
        }
        return uuid != null && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
