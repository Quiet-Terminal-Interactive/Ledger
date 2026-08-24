package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.quietterminal.ledger.webhook.WebhookNotifier;

class WebhookNotifierTest {

    @Test
    void postsMessageTextToConfiguredUrl() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(eq("https://webhook.example/receiver"), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        WebhookNotifier notifier = new WebhookNotifier(restTemplate, "https://webhook.example/receiver");

        notifier.notify("hello");

        verify(restTemplate).postForEntity(eq("https://webhook.example/receiver"), eq(Map.of("text", "hello")),
                eq(Void.class));
    }

    @Test
    void swallowsRestClientExceptionsSoACallerNeverFails() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new RestClientException("boom"));
        WebhookNotifier notifier = new WebhookNotifier(restTemplate, "https://webhook.example/receiver");

        notifier.notify("hello");
    }

    @Test
    void refusesToStartWithoutAUrl() {
        RestTemplate restTemplate = mock(RestTemplate.class);

        assertThrows(IllegalStateException.class, () -> new WebhookNotifier(restTemplate, ""));
        assertThrows(IllegalStateException.class, () -> new WebhookNotifier(restTemplate, null));
    }
}
