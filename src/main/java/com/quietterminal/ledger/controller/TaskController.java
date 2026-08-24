package com.quietterminal.ledger.controller;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.entity.Activity;
import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.enums.ActivityType;
import com.quietterminal.ledger.enums.TaskStatus;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.TaskCreationInvalidException;
import com.quietterminal.ledger.error.TaskNotFoundException;
import com.quietterminal.ledger.error.UserNotFoundException;
import com.quietterminal.ledger.event.TaskAssignedEvent;
import com.quietterminal.ledger.event.TaskBlockedEvent;
import com.quietterminal.ledger.event.TaskCompletedEvent;
import com.quietterminal.ledger.event.TaskCreatedEvent;
import com.quietterminal.ledger.repository.TaskRepository;
import com.quietterminal.ledger.repository.UserRepository;
import com.quietterminal.ledger.security.LedgerPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TaskController(TaskRepository taskRepository, UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public ResponseEntity<TaskView> createTask(@AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody CreateTaskRequest request) {
        User creator = resolveActor(principal);
        Set<User> assignees = resolveUsers(request.assigneeIds());
        Task task = new Task(request.title(), request.description(), assignees, request.dueDate());
        task.recordActivity(ActivityType.CREATED, creator);
        if (!assignees.isEmpty()) {
            Activity assignedActivity = task.recordActivity(ActivityType.ASSIGNED, creator);
            assignedActivity.putMetadata("assignees", assignees);
        }
        resolveBlockers(request.blockedByIds()).forEach(task::addBlocker);
        taskRepository.save(task);
        eventPublisher.publishEvent(new TaskCreatedEvent(task, creator));
        if (!assignees.isEmpty()) {
            eventPublisher.publishEvent(new TaskAssignedEvent(task, assignees, creator));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(task));
    }

    @GetMapping
    public List<TaskView> listTasks() {
        return taskRepository.findAll().stream().map(TaskController::toView).toList();
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskView> getTask(@PathVariable("taskId") UUID taskId) {
        return taskRepository.findById(taskId)
                .map(task -> ResponseEntity.ok(toView(task)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/assigned/{userId}")
    public ResponseEntity<List<TaskView>> listTasksAssignedToUser(@PathVariable("userId") UUID userId) {
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }
        List<TaskView> views = taskRepository.findByAssignees_Uuid(userId).stream()
                .map(TaskController::toView)
                .toList();
        return ResponseEntity.ok(views);
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<TaskView> updateTask(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("taskId") UUID taskId, @RequestBody UpdateTaskRequest request) {
        User actor = resolveActor(principal);
        return taskRepository.findById(taskId).map(task -> {
            if (request.title() != null) {
                task.setTitle(request.title());
                task.recordActivity(ActivityType.TITLE_UPDATED, actor);
            }
            if (request.description() != null) {
                task.setDescription(request.description());
                task.recordActivity(ActivityType.DESCRIPTION_UPDATED, actor);
            }
            if (request.dueDate() != null) {
                task.setDueDate(request.dueDate());
                task.recordActivity(ActivityType.DUE_DATE_UPDATED, actor);
            }
            taskRepository.save(task);
            return ResponseEntity.ok(toView(task));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskView> changeStatus(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("taskId") UUID taskId, @Valid @RequestBody ChangeStatusRequest request) {
        User actor = resolveActor(principal);
        return taskRepository.findById(taskId).map(task -> {
            task.setStatus(request.status());
            task.recordActivity(ActivityType.STATUS_UPDATED, actor);
            taskRepository.save(task);
            if (request.status() == TaskStatus.COMPLETED) {
                eventPublisher.publishEvent(new TaskCompletedEvent(task, actor));
            }
            return ResponseEntity.ok(toView(task));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/assignees/{userId}")
    public ResponseEntity<TaskView> assignUser(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("taskId") UUID taskId, @PathVariable("userId") UUID userId) {
        User actor = resolveActor(principal);
        return taskRepository.findById(taskId).map(task -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("No user found with id " + userId + "."));
            task.assign(user);
            ActivityType activityType = principal.userId().equals(userId) ? ActivityType.CLAIMED
                    : ActivityType.ASSIGNED;
            Activity activity = task.recordActivity(activityType, actor);
            if (activityType.equals(ActivityType.ASSIGNED)) {
                activity.putMetadata("assignedTo", user);
            }
            taskRepository.save(task);
            if (activityType.equals(ActivityType.ASSIGNED)) {
                eventPublisher.publishEvent(new TaskAssignedEvent(task, Set.of(user), actor));
            }
            return ResponseEntity.ok(toView(task));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{taskId}/assignees/{userId}")
    public ResponseEntity<TaskView> unassignUser(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("taskId") UUID taskId, @PathVariable("userId") UUID userId) {
        User actor = resolveActor(principal);
        return taskRepository.findById(taskId).map(task -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("No user found with id " + userId + "."));
            task.unassign(user);
            Activity activity = task.recordActivity(ActivityType.UNASSIGNED, actor);
            activity.putMetadata("removed", user);
            taskRepository.save(task);
            return ResponseEntity.ok(toView(task));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/blockers/{blockerId}")
    public ResponseEntity<TaskView> addBlocker(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("taskId") UUID taskId, @PathVariable("blockerId") UUID blockerId) {
        User actor = resolveActor(principal);
        return taskRepository.findById(taskId).map(task -> {
            Task blocker = taskRepository.findById(blockerId)
                    .orElseThrow(() -> new TaskNotFoundException("No task found with id " + blockerId + "."));
            task.addBlocker(blocker);
            Activity activity = task.recordActivity(ActivityType.BLOCKED, actor);
            activity.putMetadata("blocker", blocker.getUUID());
            taskRepository.save(task);
            eventPublisher.publishEvent(new TaskBlockedEvent(task, blocker, actor));
            return ResponseEntity.ok(toView(task));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{taskId}/blockers/{blockerId}")
    public ResponseEntity<TaskView> removeBlocker(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("taskId") UUID taskId, @PathVariable("blockerId") UUID blockerId) {
        User actor = resolveActor(principal);
        return taskRepository.findById(taskId).map(task -> {
            Task blocker = taskRepository.findById(blockerId)
                    .orElseThrow(() -> new TaskNotFoundException("No task found with id " + blockerId + "."));
            task.removeBlocker(blocker);
            Activity activity = task.recordActivity(ActivityType.UNBLOCKED, actor);
            activity.putMetadata("blocker", blocker.getUUID());
            taskRepository.save(task);
            return ResponseEntity.ok(toView(task));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("taskId") UUID taskId) {
        if (!taskRepository.existsById(taskId)) {
            return ResponseEntity.notFound().build();
        }
        taskRepository.deleteById(taskId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status = e instanceof TaskNotFoundException || e instanceof UserNotFoundException
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(e.getMessage());
    }

    private User resolveActor(LedgerPrincipal principal) {
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new UserNotFoundException("No user found with id " + principal.userId() + "."));
    }

    private Set<User> resolveUsers(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<User> users = new HashSet<>(userRepository.findAllById(userIds));
        if (users.size() != new HashSet<>(userIds).size()) {
            throw new TaskCreationInvalidException("One or more assignee ids do not correspond to an existing user.");
        }
        return users;
    }

    private Set<Task> resolveBlockers(Set<UUID> blockerIds) {
        if (blockerIds == null || blockerIds.isEmpty()) {
            return Set.of();
        }
        Set<Task> blockers = new HashSet<>(taskRepository.findAllById(blockerIds));
        if (blockers.size() != new HashSet<>(blockerIds).size()) {
            throw new TaskCreationInvalidException("One or more blocker ids do not correspond to an existing task.");
        }
        return blockers;
    }

    private static TaskView toView(Task task) {
        Set<UUID> assigneeIds = task.getAssignees().stream().map(User::getUUID).collect(Collectors.toSet());
        Set<UUID> blockedByIds = task.getBlockedBy().stream().map(Task::getUUID).collect(Collectors.toSet());
        return new TaskView(task.getUUID(), task.getTitle(), task.getDescription(), task.getDueDate(), assigneeIds,
                blockedByIds, task.isBlocked(), task.getStatus());
    }

    public record CreateTaskRequest(@NotBlank String title, String description, LocalDate dueDate,
            Set<UUID> assigneeIds, Set<UUID> blockedByIds) {
    }

    public record UpdateTaskRequest(String title, String description, LocalDate dueDate) {
    }

    public record ChangeStatusRequest(@NotNull TaskStatus status) {
    }

    public record TaskView(UUID id, String title, String description, LocalDate dueDate, Set<UUID> assigneeIds,
            Set<UUID> blockedByIds, boolean blocked, TaskStatus status) {
    }
}
