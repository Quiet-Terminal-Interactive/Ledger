package com.quietterminal.ledger.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.WikiPage;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.UserNotFoundException;
import com.quietterminal.ledger.error.WikiPageInvalidException;
import com.quietterminal.ledger.event.WikiPageCreatedEvent;
import com.quietterminal.ledger.repository.UserRepository;
import com.quietterminal.ledger.repository.WikiPageRepository;
import com.quietterminal.ledger.security.LedgerPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/wiki")
public class WikiController {

    private final WikiPageRepository wikiPageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WikiController(WikiPageRepository wikiPageRepository, UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.wikiPageRepository = wikiPageRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public ResponseEntity<WikiPageView> createPage(@AuthenticationPrincipal LedgerPrincipal principal,
            @Valid @RequestBody CreateWikiPageRequest request) {
        User actor = resolveActor(principal);
        String normalizedPath = WikiPage.normalizePath(request.path());
        if (wikiPageRepository.findByPath(normalizedPath).isPresent()) {
            throw new WikiPageInvalidException("A wiki page already exists at path \"" + normalizedPath + "\".");
        }
        WikiPage page = new WikiPage(request.path(), request.title(), request.content(), actor);
        wikiPageRepository.save(page);
        eventPublisher.publishEvent(new WikiPageCreatedEvent(page, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(page));
    }

    @GetMapping
    public List<WikiPageView> listPages() {
        return wikiPageRepository.findAllByOrderByPathAsc().stream().map(WikiController::toView).toList();
    }

    @GetMapping("/{pageId}")
    public ResponseEntity<WikiPageView> getPage(@PathVariable("pageId") UUID pageId) {
        return wikiPageRepository.findById(pageId)
                .map(page -> ResponseEntity.ok(toView(page)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-path")
    public ResponseEntity<WikiPageView> getPageByPath(@RequestParam("path") String path) {
        return wikiPageRepository.findByPath(WikiPage.normalizePath(path))
                .map(page -> ResponseEntity.ok(toView(page)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{pageId}")
    public ResponseEntity<WikiPageView> updatePage(@PathVariable("pageId") UUID pageId,
            @RequestBody UpdateWikiPageRequest request) {
        return wikiPageRepository.findById(pageId).map(page -> {
            if (request.title() != null) {
                page.setTitle(request.title());
            }
            if (request.content() != null) {
                page.setContent(request.content());
            }
            wikiPageRepository.save(page);
            return ResponseEntity.ok(toView(page));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<Void> deletePage(@PathVariable("pageId") UUID pageId) {
        if (!wikiPageRepository.existsById(pageId)) {
            return ResponseEntity.notFound().build();
        }
        wikiPageRepository.deleteById(pageId);
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

    private static WikiPageView toView(WikiPage page) {
        return new WikiPageView(page.getUUID(), page.getPath(), page.getTitle(), page.getContent(),
                page.getCreatedBy().getUUID(), page.getCreatedAt(), page.getUpdatedAt());
    }

    public record CreateWikiPageRequest(@NotBlank String path, @NotBlank String title, String content) {
    }

    public record UpdateWikiPageRequest(String title, String content) {
    }

    public record WikiPageView(UUID id, String path, String title, String content, UUID createdBy,
            Instant createdAt, Instant updatedAt) {
    }
}
