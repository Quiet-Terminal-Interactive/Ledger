package com.quietterminal.ledger.notification;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public abstract class AbstractWebhookNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(AbstractWebhookNotifier.class);

    private final RestTemplate restTemplate;
    private final String webhookUrl;
    private final String payloadField;
    private final String id;
    private final String displayName;

    protected AbstractWebhookNotifier(RestTemplate restTemplate, String webhookUrl, String payloadField,
            String configKeyDescription, String id, String displayName) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalStateException(configKeyDescription + " must be set when notifications are enabled.");
        }
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
        this.payloadField = payloadField;
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public void notify(String message) {
        try {
            restTemplate.postForEntity(webhookUrl, Map.of(payloadField, message), Void.class);
        } catch (RestClientException e) {
            log.error("Failed to post webhook notification to {}: {}", webhookUrl, message, e);
        }
    }
}
