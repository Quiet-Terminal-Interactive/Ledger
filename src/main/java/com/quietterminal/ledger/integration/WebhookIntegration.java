package com.quietterminal.ledger.integration;

import java.util.Optional;

import org.springframework.http.HttpHeaders;

public interface WebhookIntegration extends Integration {

    @Override
    default IntegrationKind kind() {
        return IntegrationKind.INBOUND_WEBHOOK;
    }

    Optional<String> handle(HttpHeaders headers, String rawBody);
}
