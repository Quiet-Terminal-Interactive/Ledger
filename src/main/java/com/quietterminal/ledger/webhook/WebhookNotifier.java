package com.quietterminal.ledger.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.quietterminal.ledger.notification.AbstractWebhookNotifier;

@Component
@ConditionalOnProperty(name = "ledger.webhook.enabled", havingValue = "true")
public class WebhookNotifier extends AbstractWebhookNotifier {

    public WebhookNotifier(RestTemplate restTemplate, @Value("${ledger.webhook.url}") String webhookUrl) {
        super(restTemplate, webhookUrl, "text", "ledger.webhook.url (env LEDGER_WEBHOOK_URL)", "webhook",
                "Generic Webhook");
    }
}
