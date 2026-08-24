package com.quietterminal.ledger.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.error.IntegrationNotFoundException;
import com.quietterminal.ledger.error.IntegrationWebhookUnauthorizedException;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.integration.IntegrationRegistry;
import com.quietterminal.ledger.integration.WebhookIntegration;
import com.quietterminal.ledger.notification.Notifier;

@RestController
@RequestMapping("/integrations/webhooks")
public class IntegrationWebhookController {

    private final IntegrationRegistry registry;
    private final List<Notifier> notifiers;

    public IntegrationWebhookController(IntegrationRegistry registry, List<Notifier> notifiers) {
        this.registry = registry;
        this.notifiers = notifiers;
    }

    @PostMapping("/{id}")
    public ResponseEntity<Void> receive(@PathVariable("id") String id, @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) String rawBody) {
        WebhookIntegration integration = registry.findInboundWebhook(id)
                .orElseThrow(() -> new IntegrationNotFoundException(
                        "No inbound webhook integration registered for '" + id + "'."));

        integration.handle(headers, rawBody == null ? "" : rawBody)
                .ifPresent(message -> notifiers.forEach(notifier -> notifier.notify(message)));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status = e instanceof IntegrationNotFoundException ? HttpStatus.NOT_FOUND
                : e instanceof IntegrationWebhookUnauthorizedException ? HttpStatus.UNAUTHORIZED
                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(e.getMessage());
    }
}
