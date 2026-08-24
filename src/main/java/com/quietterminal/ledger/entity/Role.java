package com.quietterminal.ledger.entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.quietterminal.ledger.enums.Permission;
import com.quietterminal.ledger.error.RoleInvalidException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private Set<Permission> permissions;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    protected Role() {
        this.permissions = new HashSet<>();
    }

    public Role(String name, Set<Permission> permissions) {
        this(name, permissions, false);
    }

    public Role(String name, Set<Permission> permissions, boolean builtIn) {
        Objects.requireNonNull(permissions, "Permissions cannot be null.");
        validateNameOrThrow(name);

        this.id = UUID.randomUUID();
        this.name = name;
        this.permissions = new HashSet<>(permissions);
        this.builtIn = builtIn;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        validateNameOrThrow(name);
        this.name = name;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public void setPermissions(Set<Permission> permissions) {
        Objects.requireNonNull(permissions, "Permissions cannot be null.");
        this.permissions = new HashSet<>(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return this.permissions.contains(permission);
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    private static void validateNameOrThrow(String name) {
        if (name == null || name.isBlank()) {
            throw new RoleInvalidException("Role name cannot be blank.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
