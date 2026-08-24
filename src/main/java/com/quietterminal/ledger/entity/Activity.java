package com.quietterminal.ledger.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.quietterminal.ledger.enums.ActivityType;
import com.quietterminal.ledger.persistence.JsonMapConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @Column(nullable = false)
    private Long timestamp;

    @Lob
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    protected Activity() {
    }

    public Activity(Task task, ActivityType activity, User user) {
        this.uuid = UUID.randomUUID();
        this.task = Objects.requireNonNull(task, "Task cannot be null.");
        this.activity = Objects.requireNonNull(activity, "Activity cannot be null.");
        this.user = user;
        this.timestamp = System.currentTimeMillis() / 1000;
        this.metadata = new HashMap<>();
    }

    public UUID getUUID() {
        return uuid;
    }

    public ActivityType getActivity() {
        return activity;
    }

    public User getUser() {
        return user;
    }

    public Task getTask() {
        return task;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public void putMetadata(String key, Object value) {
        this.metadata.put(Objects.requireNonNull(key, "Metadata key cannot be null."), value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Activity other)) {
            return false;
        }
        return uuid != null && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
