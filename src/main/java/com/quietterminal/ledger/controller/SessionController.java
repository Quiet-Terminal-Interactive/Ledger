package com.quietterminal.ledger.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.entity.Session;
import com.quietterminal.ledger.repository.SessionRepository;
import com.quietterminal.ledger.security.LedgerPrincipal;

@RestController
@RequestMapping("/auth/sessions")
public class SessionController {

    private final SessionRepository sessionRepository;

    public SessionController(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public Iterable<SessionView> listSessions(@AuthenticationPrincipal LedgerPrincipal principal) {
        return sessionRepository
                .findByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(principal.userId(), Instant.now())
                .stream()
                .map(session -> toView(session, principal.sessionId()))
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal LedgerPrincipal principal,
            @PathVariable("sessionId") UUID sessionId) {
        long deleted = sessionRepository.deleteByIdAndUserId(sessionId, principal.userId());

        if (deleted == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    private static SessionView toView(Session session, UUID currentSessionId) {
        return new SessionView(session.getId(), session.getCreatedAt(), session.getExpiresAt(),
                session.getUserAgent(), session.getId().equals(currentSessionId));
    }

    public record SessionView(UUID id, Instant createdAt, Instant expiresAt, String userAgent, boolean current) {
    }
}
