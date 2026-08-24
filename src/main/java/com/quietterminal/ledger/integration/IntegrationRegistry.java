package com.quietterminal.ledger.integration;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class IntegrationRegistry {

    private final List<Integration> integrations;

    public IntegrationRegistry(List<Integration> integrations) {
        this.integrations = integrations;
    }

    public List<Integration> all() {
        return List.copyOf(integrations);
    }

    public Optional<WebhookIntegration> findInboundWebhook(String id) {
        return integrations.stream()
                .filter(integration -> integration instanceof WebhookIntegration && integration.id().equals(id))
                .map(WebhookIntegration.class::cast)
                .findFirst();
    }
}
