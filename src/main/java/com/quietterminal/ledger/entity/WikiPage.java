package com.quietterminal.ledger.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.quietterminal.ledger.error.WikiPageInvalidException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "wiki_pages")
public class WikiPage {

    private static final String PATH_SEGMENT_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    @Column(nullable = false, unique = true, updatable = false)
    private String path;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WikiPage() {
    }

    public WikiPage(String path, String title, String content, User createdBy) {
        Objects.requireNonNull(createdBy, "Creator cannot be null.");
        if (title == null || title.isBlank()) {
            throw new WikiPageInvalidException("Title cannot be blank.");
        }

        this.uuid = UUID.randomUUID();
        this.path = normalizePath(path);
        this.title = title;
        this.content = content == null ? "" : content;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static String normalizePath(String path) {
        if (path == null) {
            throw new WikiPageInvalidException("Path cannot be blank.");
        }
        String trimmed = path.strip().toLowerCase();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            throw new WikiPageInvalidException("Path cannot be blank.");
        }
        for (String segment : trimmed.split("/")) {
            if (!segment.matches(PATH_SEGMENT_PATTERN)) {
                throw new WikiPageInvalidException(
                        "Path segments may only contain lowercase letters, numbers, and hyphens.");
            }
        }
        return trimmed;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getPath() {
        return path;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new WikiPageInvalidException("Title cannot be blank.");
        }
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
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
        if (!(o instanceof WikiPage other)) {
            return false;
        }
        return uuid != null && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
