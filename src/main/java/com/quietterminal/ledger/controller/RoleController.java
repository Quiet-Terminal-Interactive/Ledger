package com.quietterminal.ledger.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.enums.Permission;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.RoleDeletionConflictException;
import com.quietterminal.ledger.error.RoleInvalidException;
import com.quietterminal.ledger.error.RoleNotFoundException;
import com.quietterminal.ledger.repository.RoleRepository;
import com.quietterminal.ledger.repository.SessionRepository;
import com.quietterminal.ledger.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    public RoleController(RoleRepository roleRepository, UserRepository userRepository,
            SessionRepository sessionRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public List<RoleSummary> listRoles() {
        return roleRepository.findAll().stream()
                .map(RoleController::toSummary)
                .sorted(Comparator.comparing(RoleSummary::name))
                .toList();
    }

    @PostMapping
    public ResponseEntity<RoleSummary> createRole(@Valid @RequestBody CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new RoleInvalidException("A role named \"" + request.name() + "\" already exists.");
        }

        Role role = new Role(request.name(), request.permissions());
        roleRepository.save(role);

        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(role));
    }

    @PatchMapping("/{roleId}")
    public ResponseEntity<RoleSummary> updateRole(@PathVariable("roleId") UUID roleId,
            @RequestBody UpdateRoleRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("No role found with id " + roleId + "."));

        if (request.name() != null) {
            role.setName(request.name());
        }
        if (request.permissions() != null) {
            role.setPermissions(request.permissions());
        }
        roleRepository.save(role);

        if (request.permissions() != null) {
            userRepository.findByRole_Id(roleId)
                    .forEach(user -> sessionRepository.deleteByUserId(user.getUUID()));
        }

        return ResponseEntity.ok(toSummary(role));
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> deleteRole(@PathVariable("roleId") UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("No role found with id " + roleId + "."));

        if (role.isBuiltIn()) {
            throw new RoleDeletionConflictException("The \"" + role.getName() + "\" role is built in and cannot "
                    + "be deleted.");
        }

        try {
            roleRepository.delete(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException | InvalidDataAccessApiUsageException e) {
            throw new RoleDeletionConflictException(
                    "Cannot delete this role because it is still assigned to one or more users.");
        }

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status;
        if (e instanceof RoleNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (e instanceof RoleDeletionConflictException) {
            status = HttpStatus.CONFLICT;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(e.getMessage());
    }

    private static RoleSummary toSummary(Role role) {
        return new RoleSummary(role.getId(), role.getName(), role.getPermissions(), role.isBuiltIn());
    }

    public record RoleSummary(UUID id, String name, Set<Permission> permissions, boolean builtIn) {
    }

    public record CreateRoleRequest(@NotBlank String name, @NotNull Set<Permission> permissions) {
    }

    public record UpdateRoleRequest(String name, Set<Permission> permissions) {
    }
}
