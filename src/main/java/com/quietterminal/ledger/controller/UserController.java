package com.quietterminal.ledger.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.UserCredentials;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.RoleNotFoundException;
import com.quietterminal.ledger.error.UserCreationInvalidException;
import com.quietterminal.ledger.error.UserDeletionConflictException;
import com.quietterminal.ledger.error.UserForbiddenException;
import com.quietterminal.ledger.error.UserNotFoundException;
import com.quietterminal.ledger.error.UserUpdateInvalidException;
import com.quietterminal.ledger.repository.RoleRepository;
import com.quietterminal.ledger.repository.SessionRepository;
import com.quietterminal.ledger.repository.TaskRepository;
import com.quietterminal.ledger.repository.UserCredentialsRepository;
import com.quietterminal.ledger.repository.UserRepository;
import com.quietterminal.ledger.security.LedgerPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserCredentialsRepository credentialsRepository;
    private final SessionRepository sessionRepository;
    private final TaskRepository taskRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, UserCredentialsRepository credentialsRepository,
            SessionRepository sessionRepository, TaskRepository taskRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.credentialsRepository = credentialsRepository;
        this.sessionRepository = sessionRepository;
        this.taskRepository = taskRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserSummary> listUsers(@RequestParam(value = "query", required = false) String query) {
        List<UserCredentials> credentials = (query == null || query.isBlank())
                ? credentialsRepository.findAll()
                : credentialsRepository.findByUsernameContainingIgnoreCase(query.strip());
        if (credentials.isEmpty()) {
            return List.of();
        }

        Map<UUID, UserCredentials> credentialsByUserId = credentials.stream()
                .collect(Collectors.toMap(UserCredentials::getUserId, c -> c));

        return userRepository.findAllById(credentialsByUserId.keySet()).stream()
                .map(user -> toSummary(user, credentialsByUserId.get(user.getUUID())))
                .sorted(Comparator.comparing(UserSummary::username))
                .toList();
    }

    @PostMapping
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (credentialsRepository.existsByUsername(request.username())) {
            throw new UserCreationInvalidException("Username \"" + request.username() + "\" is already taken.");
        }
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new RoleNotFoundException("No role found with id " + request.roleId() + "."));

        User user = new User(request.firstName(), request.lastName(), role);
        userRepository.save(user);
        UserCredentials credentials = new UserCredentials(user.getUUID(), request.username(),
                passwordEncoder.encode(request.password()));
        credentialsRepository.save(credentials);

        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(user, credentials));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserSummary> updateUser(@PathVariable("userId") UUID userId,
            @RequestBody UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No user found with id " + userId + "."));
        UserCredentials credentials = credentialsRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("No credentials found for user " + userId + "."));

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        boolean roleChanged = request.roleId() != null;
        if (roleChanged) {
            Role role = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new RoleNotFoundException("No role found with id " + request.roleId() + "."));
            user.setRole(role);
        }
        userRepository.save(user);

        boolean passwordChanged = request.password() != null && !request.password().isBlank();
        if (passwordChanged) {
            credentials.setPasswordHash(passwordEncoder.encode(request.password()));
            credentialsRepository.save(credentials);
        }

        if (roleChanged || passwordChanged) {
            sessionRepository.deleteByUserId(userId);
        }

        return ResponseEntity.ok(toSummary(user, credentials));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("userId") UUID userId) {
        if (principal.userId().equals(userId)) {
            throw new UserUpdateInvalidException("You cannot delete your own account.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No user found with id " + userId + "."));

        List<Task> assignedTasks = taskRepository.findByAssignees_Uuid(userId);
        assignedTasks.forEach(task -> task.unassign(user));
        taskRepository.saveAll(assignedTasks);

        sessionRepository.deleteByUserId(userId);
        credentialsRepository.findByUserId(userId).ifPresent(credentialsRepository::delete);

        try {
            userRepository.delete(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserDeletionConflictException("Cannot delete this user because other records (tasks, "
                    + "activity history, budget entries, wiki pages, or uploads) still reference them.");
        }

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status;
        if (e instanceof UserNotFoundException || e instanceof RoleNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (e instanceof UserForbiddenException) {
            status = HttpStatus.FORBIDDEN;
        } else if (e instanceof UserDeletionConflictException) {
            status = HttpStatus.CONFLICT;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(e.getMessage());
    }

    private static UserSummary toSummary(User user, UserCredentials credentials) {
        return new UserSummary(user.getUUID(), user.getFirstName(), user.getLastName(), credentials.getUsername(),
                user.getRole().getId(), user.getRole().getName());
    }

    public record UserSummary(UUID id, String firstName, String lastName, String username, UUID roleId,
            String roleName) {
    }

    public record CreateUserRequest(@NotBlank String username, @NotBlank String password,
            @NotBlank String firstName, @NotBlank String lastName, @NotNull UUID roleId) {
    }

    public record UpdateUserRequest(String firstName, String lastName, UUID roleId, String password) {
    }
}
