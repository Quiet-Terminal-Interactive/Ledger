package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.quietterminal.ledger.controller.IntegrationController;
import com.quietterminal.ledger.controller.IntegrationController.IntegrationSummary;
import com.quietterminal.ledger.integration.Integration;
import com.quietterminal.ledger.integration.IntegrationKind;
import com.quietterminal.ledger.integration.IntegrationRegistry;
import com.quietterminal.ledger.integration.WebhookIntegration;

class IntegrationControllerTest {

    @Test
    void listsEveryIntegrationSortedByDisplayNameCaseInsensitively() {
        Integration gmail = fakeIntegration("gmail", "Gmail", IntegrationKind.OAUTH_APP);
        Integration git = fakeIntegration("git", "Git (Gitea/Forgejo)", IntegrationKind.OAUTH_APP);
        WebhookIntegration jira = fakeInboundWebhook("jira", "jira issue tracker");
        IntegrationRegistry registry = new IntegrationRegistry(List.of(gmail, git, jira));
        IntegrationController controller = new IntegrationController(registry);

        List<IntegrationSummary> summaries = controller.list();

        assertEquals(List.of(
                new IntegrationSummary("git", "Git (Gitea/Forgejo)", IntegrationKind.OAUTH_APP),
                new IntegrationSummary("gmail", "Gmail", IntegrationKind.OAUTH_APP),
                new IntegrationSummary("jira", "jira issue tracker", IntegrationKind.INBOUND_WEBHOOK)),
                summaries);
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

    private static WebhookIntegration fakeInboundWebhook(String id, String displayName) {
        return new WebhookIntegration() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public Optional<String> handle(HttpHeaders headers, String rawBody) {
                return Optional.empty();
            }
        };
    }
}
