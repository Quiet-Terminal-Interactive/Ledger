package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.quietterminal.ledger.controller.IntegrationWebhookController;
import com.quietterminal.ledger.error.IntegrationNotFoundException;
import com.quietterminal.ledger.error.IntegrationWebhookUnauthorizedException;
import com.quietterminal.ledger.integration.IntegrationRegistry;
import com.quietterminal.ledger.integration.WebhookIntegration;
import com.quietterminal.ledger.notification.Notifier;

class IntegrationWebhookControllerTest {

    @Test
    void dispatchesToTheMatchingIntegrationAndBroadcastsItsSummary() {
        WebhookIntegration jira = mock(WebhookIntegration.class);
        when(jira.id()).thenReturn("jira");
        when(jira.handle(any(), any())).thenReturn(Optional.of("Jira: issue closed"));
        IntegrationRegistry registry = new IntegrationRegistry(List.of(jira));
        Notifier slack = mock(Notifier.class);
        Notifier discord = mock(Notifier.class);
        IntegrationWebhookController controller = new IntegrationWebhookController(registry,
                List.of(slack, discord));
        HttpHeaders headers = new HttpHeaders();

        ResponseEntity<Void> response = controller.receive("jira", headers, "{}");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(slack).notify("Jira: issue closed");
        verify(discord).notify("Jira: issue closed");
    }

    @Test
    void doesNotBroadcastWhenTheIntegrationHandlesTheEventSilently() {
        WebhookIntegration jira = mock(WebhookIntegration.class);
        when(jira.id()).thenReturn("jira");
        when(jira.handle(any(), any())).thenReturn(Optional.empty());
        IntegrationRegistry registry = new IntegrationRegistry(List.of(jira));
        Notifier slack = mock(Notifier.class);
        IntegrationWebhookController controller = new IntegrationWebhookController(registry, List.of(slack));

        controller.receive("jira", new HttpHeaders(), "{}");

        verify(slack, never()).notify(any());
    }

    @Test
    void unknownIntegrationIdThrowsNotFound() {
        IntegrationRegistry registry = new IntegrationRegistry(List.of());
        IntegrationWebhookController controller = new IntegrationWebhookController(registry, List.of());

        assertThrows(IntegrationNotFoundException.class,
                () -> controller.receive("unknown", new HttpHeaders(), "{}"));
    }

    @Test
    void anIntegrationRejectingTheRequestPropagatesUnauthorized() {
        WebhookIntegration jira = mock(WebhookIntegration.class);
        when(jira.id()).thenReturn("jira");
        when(jira.handle(any(), any())).thenThrow(new IntegrationWebhookUnauthorizedException("bad signature"));
        IntegrationRegistry registry = new IntegrationRegistry(List.of(jira));
        IntegrationWebhookController controller = new IntegrationWebhookController(registry, List.of());

        assertThrows(IntegrationWebhookUnauthorizedException.class,
                () -> controller.receive("jira", new HttpHeaders(), "{}"));
    }

    @Test
    void exceptionHandlerMapsErrorCodesToHttpStatuses() {
        IntegrationWebhookController controller = new IntegrationWebhookController(
                new IntegrationRegistry(List.of()), List.of());

        assertEquals(HttpStatus.NOT_FOUND,
                controller.handleLedgerError(new IntegrationNotFoundException("nope")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.handleLedgerError(new IntegrationWebhookUnauthorizedException("nope")).getStatusCode());
    }
}
