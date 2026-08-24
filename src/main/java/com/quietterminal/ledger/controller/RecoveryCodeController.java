package com.quietterminal.ledger.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.security.LedgerPrincipal;
import com.quietterminal.ledger.security.RecoveryCodeService;

@RestController
@RequestMapping("/auth/recovery-codes")
public class RecoveryCodeController {

    private final RecoveryCodeService recoveryCodeService;

    public RecoveryCodeController(RecoveryCodeService recoveryCodeService) {
        this.recoveryCodeService = recoveryCodeService;
    }

    @GetMapping
    public StatusResponse status(@AuthenticationPrincipal LedgerPrincipal principal) {
        return toResponse(recoveryCodeService.status(principal.userId()));
    }

    @PostMapping
    public GenerateResponse regenerate(@AuthenticationPrincipal LedgerPrincipal principal) {
        List<String> codes = recoveryCodeService.regenerate(principal.userId());
        return new GenerateResponse(codes);
    }

    @DeleteMapping
    public ResponseEntity<Void> revokeAll(@AuthenticationPrincipal LedgerPrincipal principal) {
        recoveryCodeService.revokeAll(principal.userId());
        return ResponseEntity.noContent().build();
    }

    private static StatusResponse toResponse(RecoveryCodeService.Status status) {
        return new StatusResponse(status.total(), status.remaining(), status.generatedAt());
    }

    public record StatusResponse(int total, int remaining, Instant generatedAt) {
    }

    public record GenerateResponse(List<String> codes) {
    }
}
