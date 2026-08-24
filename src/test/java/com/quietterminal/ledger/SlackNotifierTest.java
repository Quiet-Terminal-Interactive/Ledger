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

import com.quietterminal.ledger.slack.SlackNotifier;

class SlackNotifierTest {

    @Test
    void postsMessageTextToConfiguredWebhookUrl() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(eq("https://slack.example/webhook"), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        SlackNotifier notifier = new SlackNotifier(restTemplate, "https://slack.example/webhook");

        notifier.notify("hello");

        verify(restTemplate).postForEntity(eq("https://slack.example/webhook"), eq(Map.of("text", "hello")),
                eq(Void.class));
    }

    @Test
    void swallowsRestClientExceptionsSoACallerNeverFails() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new RestClientException("boom"));
        SlackNotifier notifier = new SlackNotifier(restTemplate, "https://slack.example/webhook");

        notifier.notify("hello");
    }

    @Test
    void refusesToStartWithoutAWebhookUrl() {
        RestTemplate restTemplate = mock(RestTemplate.class);

        assertThrows(IllegalStateException.class, () -> new SlackNotifier(restTemplate, ""));
        assertThrows(IllegalStateException.class, () -> new SlackNotifier(restTemplate, null));
    }
}
