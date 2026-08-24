package com.quietterminal.ledger.entity;

import java.util.Objects;

public class Repo {

    private final String owner;
    private final String name;
    private final String description;
    private final String defaultBranch;

    public Repo(String owner, String name, String description, String defaultBranch) {
        this.owner = Objects.requireNonNull(owner, "Owner cannot be null.");
        this.name = Objects.requireNonNull(name, "Name cannot be null.");
        this.description = description;
        this.defaultBranch = defaultBranch;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Repo other)) {
            return false;
        }
        return owner.equalsIgnoreCase(other.owner) && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner.toLowerCase(), name);
    }
}
