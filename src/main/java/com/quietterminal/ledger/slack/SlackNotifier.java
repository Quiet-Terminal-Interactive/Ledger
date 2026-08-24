package com.quietterminal.ledger.slack;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.quietterminal.ledger.notification.AbstractWebhookNotifier;

@Component
@ConditionalOnProperty(name = "ledger.slack.enabled", havingValue = "true")
public class SlackNotifier extends AbstractWebhookNotifier {

    public SlackNotifier(RestTemplate restTemplate, @Value("${ledger.slack.webhook-url}") String webhookUrl) {
        super(restTemplate, webhookUrl, "text", "ledger.slack.webhook-url (env LEDGER_SLACK_WEBHOOK_URL)", "slack",
                "Slack");
    }
}
