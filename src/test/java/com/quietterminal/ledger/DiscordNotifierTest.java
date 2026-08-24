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

import com.quietterminal.ledger.discord.DiscordNotifier;

class DiscordNotifierTest {

    @Test
    void postsMessageContentToConfiguredWebhookUrl() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(eq("https://discord.example/webhook"), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        DiscordNotifier notifier = new DiscordNotifier(restTemplate, "https://discord.example/webhook");

        notifier.notify("hello");

        verify(restTemplate).postForEntity(eq("https://discord.example/webhook"), eq(Map.of("content", "hello")),
                eq(Void.class));
    }

    @Test
    void swallowsRestClientExceptionsSoACallerNeverFails() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new RestClientException("boom"));
        DiscordNotifier notifier = new DiscordNotifier(restTemplate, "https://discord.example/webhook");

        notifier.notify("hello");
    }

    @Test
    void refusesToStartWithoutAWebhookUrl() {
        RestTemplate restTemplate = mock(RestTemplate.class);

        assertThrows(IllegalStateException.class, () -> new DiscordNotifier(restTemplate, ""));
        assertThrows(IllegalStateException.class, () -> new DiscordNotifier(restTemplate, null));
    }
}
