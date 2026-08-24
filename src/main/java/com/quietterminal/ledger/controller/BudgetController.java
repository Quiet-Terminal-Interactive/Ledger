package com.quietterminal.ledger.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

import com.quietterminal.ledger.config.LedgerCurrency;
import com.quietterminal.ledger.entity.BudgetEntry;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.enums.BudgetEntryType;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.UserNotFoundException;
import com.quietterminal.ledger.event.BudgetEntryCreatedEvent;
import com.quietterminal.ledger.repository.BudgetEntryRepository;
import com.quietterminal.ledger.repository.UserRepository;
import com.quietterminal.ledger.security.LedgerPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/budget")
public class BudgetController {

    private final BudgetEntryRepository budgetEntryRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LedgerCurrency currency;

    public BudgetController(BudgetEntryRepository budgetEntryRepository, UserRepository userRepository,
            ApplicationEventPublisher eventPublisher, LedgerCurrency currency) {
        this.budgetEntryRepository = budgetEntryRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.currency = currency;
    }

    @PostMapping
    public ResponseEntity<BudgetEntryView> createEntry(@AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody CreateBudgetEntryRequest request) {
        User actor = resolveActor(principal);
        BudgetEntry entry = new BudgetEntry(request.name(), request.description(), request.amount(), request.type(),
                actor);
        budgetEntryRepository.save(entry);
        eventPublisher.publishEvent(new BudgetEntryCreatedEvent(entry, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(entry));
    }

    @GetMapping
    public List<BudgetEntryView> listEntries() {
        return budgetEntryRepository.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    @GetMapping("/{entryId}")
    public ResponseEntity<BudgetEntryView> getEntry(@PathVariable("entryId") UUID entryId) {
        return budgetEntryRepository.findById(entryId)
                .map(entry -> ResponseEntity.ok(toView(entry)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{entryId}")
    public ResponseEntity<BudgetEntryView> updateEntry(@PathVariable("entryId") UUID entryId,
            @RequestBody UpdateBudgetEntryRequest request) {
        return budgetEntryRepository.findById(entryId).map(entry -> {
            if (request.name() != null) {
                entry.setName(request.name());
            }
            if (request.description() != null) {
                entry.setDescription(request.description());
            }
            if (request.amount() != null) {
                entry.setAmount(request.amount());
            }
            if (request.type() != null) {
                entry.setType(request.type());
            }
            budgetEntryRepository.save(entry);
            return ResponseEntity.ok(toView(entry));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> deleteEntry(@PathVariable("entryId") UUID entryId) {
        if (!budgetEntryRepository.existsById(entryId)) {
            return ResponseEntity.notFound().build();
        }
        budgetEntryRepository.deleteById(entryId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status = e instanceof UserNotFoundException ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(e.getMessage());
    }

    private User resolveActor(LedgerPrincipal principal) {
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new UserNotFoundException("No user found with id " + principal.userId() + "."));
    }

    private BudgetEntryView toView(BudgetEntry entry) {
        return new BudgetEntryView(entry.getUUID(), entry.getName(), entry.getDescription(), entry.getAmount(),
                entry.getType(), currency.getCode(), entry.getCreatedBy().getUUID(), entry.getCreatedAt(),
                entry.getUpdatedAt());
    }

    public record CreateBudgetEntryRequest(@NotBlank String name, String description,
            @NotNull BigDecimal amount, @NotNull BudgetEntryType type) {
    }

    public record UpdateBudgetEntryRequest(String name, String description, BigDecimal amount,
            BudgetEntryType type) {
    }

    public record BudgetEntryView(UUID id, String name, String description, BigDecimal amount,
            BudgetEntryType type, String currency, UUID createdBy, Instant createdAt, Instant updatedAt) {
    }
}
