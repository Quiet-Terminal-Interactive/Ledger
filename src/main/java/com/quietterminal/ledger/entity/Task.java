package com.quietterminal.ledger.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.quietterminal.ledger.enums.ActivityType;
import com.quietterminal.ledger.enums.TaskStatus;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.TaskCreationInvalidException;
import com.quietterminal.ledger.error.TaskUpdateInvalidException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class Task {

    private static final String TITLE_ERROR_MESSAGE = "Title cannot be blank.";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column
    private LocalDate dueDate;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "task_assignees", joinColumns = @JoinColumn(name = "task_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> assignees;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "task_blocks", joinColumns = @JoinColumn(name = "task_id"), inverseJoinColumns = @JoinColumn(name = "blocker_id"))
    private Set<Task> blockedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @OneToMany(mappedBy = "task", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    private List<Activity> activityTracker;

    protected Task() {
        this.assignees = new HashSet<>();
        this.blockedBy = new HashSet<>();
        this.activityTracker = new ArrayList<>();
    }

    public Task(String title, String description) {
        this(title, description, new HashSet<>(), null);
    }

    public Task(String title, String description, Set<User> assignees) {
        this(title, description, assignees, null);
    }

    public Task(String title, String description, Set<User> assignees, LocalDate dueDate) {
        Objects.requireNonNull(assignees, "Assignees cannot be null.");

        validateTitleOrThrow(title, TaskCreationInvalidException::new);

        this.uuid = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.assignees = new HashSet<>(assignees);
        this.blockedBy = new HashSet<>();
        this.activityTracker = new ArrayList<>();
        this.status = TaskStatus.NOTSTARTED;
        this.dueDate = dueDate;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        validateTitleOrThrow(title, TaskUpdateInvalidException::new);
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        Objects.requireNonNull(status, "Status cannot be null.");
        if (status == TaskStatus.COMPLETED && isBlocked()) {
            throw new TaskUpdateInvalidException("Cannot complete a task while it is still blocked by open tasks.");
        }
        this.status = status;
    }

    public Set<User> getAssignees() {
        return Collections.unmodifiableSet(assignees);
    }

    public boolean isAssigned(User user) {
        return this.assignees.contains(user);
    }

    public void assign(User user) {
        this.assignees.add(Objects.requireNonNull(user, "User cannot be null."));
    }

    public void unassign(User user) {
        this.assignees.remove(user);
    }

    public Set<Task> getBlockedBy() {
        return Collections.unmodifiableSet(blockedBy);
    }

    public boolean isBlockedByTask(Task task) {
        return this.blockedBy.contains(task);
    }

    public boolean isBlocked() {
        return this.blockedBy.stream().anyMatch(blocker -> blocker.getStatus() != TaskStatus.COMPLETED);
    }

    public void addBlocker(Task blocker) {
        Objects.requireNonNull(blocker, "Blocker cannot be null.");
        if (blocker.equals(this)) {
            throw new TaskUpdateInvalidException("A task cannot block itself.");
        }
        if (blocker.isBlockedByTask(this)) {
            throw new TaskUpdateInvalidException("This would create a circular block between tasks.");
        }
        this.blockedBy.add(blocker);
    }

    public void removeBlocker(Task blocker) {
        this.blockedBy.remove(blocker);
    }

    public List<Activity> getActivityTracker() {
        return Collections.unmodifiableList(activityTracker);
    }

    public Activity recordActivity(ActivityType activityType, User user) {
        Activity activity = new Activity(this, activityType, user);
        this.activityTracker.add(activity);
        return activity;
    }

    private static void validateTitleOrThrow(String title, Function<String, ? extends LedgerError> errorFactory) {
        if (title == null || title.isBlank()) {
            throw errorFactory.apply(TITLE_ERROR_MESSAGE);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Task other)) {
            return false;
        }
        return uuid != null && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
