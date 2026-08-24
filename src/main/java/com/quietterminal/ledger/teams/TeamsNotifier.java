package com.quietterminal.ledger.teams;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.quietterminal.ledger.notification.AbstractWebhookNotifier;

@Component
@ConditionalOnProperty(name = "ledger.teams.enabled", havingValue = "true")
public class TeamsNotifier extends AbstractWebhookNotifier {

    public TeamsNotifier(RestTemplate restTemplate, @Value("${ledger.teams.webhook-url}") String webhookUrl) {
        super(restTemplate, webhookUrl, "text", "ledger.teams.webhook-url (env LEDGER_TEAMS_WEBHOOK_URL)", "teams",
                "Microsoft Teams");
    }
}
