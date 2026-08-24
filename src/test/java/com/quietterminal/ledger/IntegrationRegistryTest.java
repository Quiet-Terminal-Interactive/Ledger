package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.quietterminal.ledger.integration.Integration;
import com.quietterminal.ledger.integration.IntegrationKind;
import com.quietterminal.ledger.integration.IntegrationRegistry;
import com.quietterminal.ledger.integration.WebhookIntegration;

class IntegrationRegistryTest {

    @Test
    void allReturnsEveryRegisteredIntegration() {
        Integration git = fakeIntegration("git", "Git", IntegrationKind.OAUTH_APP);
        Integration jira = fakeInboundWebhook("jira");
        IntegrationRegistry registry = new IntegrationRegistry(List.of(git, jira));

        assertEquals(List.of(git, jira), registry.all());
    }

    @Test
    void findInboundWebhookMatchesByIdAmongWebhookIntegrationsOnly() {
        Integration git = fakeIntegration("git", "Git", IntegrationKind.OAUTH_APP);
        WebhookIntegration jira = fakeInboundWebhook("jira");
        IntegrationRegistry registry = new IntegrationRegistry(List.of(git, jira));

        Optional<WebhookIntegration> found = registry.findInboundWebhook("jira");

        assertTrue(found.isPresent());
        assertEquals(jira, found.get());
    }

    @Test
    void findInboundWebhookIsEmptyWhenIdIsUnknownOrNotAWebhookIntegration() {
        Integration git = fakeIntegration("git", "Git", IntegrationKind.OAUTH_APP);
        IntegrationRegistry registry = new IntegrationRegistry(List.of(git));

        assertTrue(registry.findInboundWebhook("git").isEmpty());
        assertTrue(registry.findInboundWebhook("unknown").isEmpty());
    }

    private static Integration fakeIntegration(String id, String displayName, IntegrationKind kind) {
        return new Integration() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public IntegrationKind kind() {
                return kind;
            }
        };
    }

    private static WebhookIntegration fakeInboundWebhook(String id) {
        return new WebhookIntegration() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return id;
            }

            @Override
            public Optional<String> handle(HttpHeaders headers, String rawBody) {
                return Optional.empty();
            }
        };
    }
}
