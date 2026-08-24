package com.quietterminal.ledger.error;

public class IntegrationWebhookUnauthorizedException extends LedgerError {
    public IntegrationWebhookUnauthorizedException(String message) {
        super("INTEGRATION_WEBHOOK_UNAUTHORIZED", message);
    }
}
