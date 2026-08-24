package com.quietterminal.ledger.discord;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.quietterminal.ledger.notification.AbstractWebhookNotifier;

@Component
@ConditionalOnProperty(name = "ledger.discord.enabled", havingValue = "true")
public class DiscordNotifier extends AbstractWebhookNotifier {

    public DiscordNotifier(RestTemplate restTemplate, @Value("${ledger.discord.webhook-url}") String webhookUrl) {
        super(restTemplate, webhookUrl, "content",
                "ledger.discord.webhook-url (env LEDGER_DISCORD_WEBHOOK_URL)", "discord", "Discord");
    }
}
