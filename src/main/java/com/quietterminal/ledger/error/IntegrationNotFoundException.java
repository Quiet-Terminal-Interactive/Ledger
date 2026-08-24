package com.quietterminal.ledger.error;

public class IntegrationNotFoundException extends LedgerError {
    public IntegrationNotFoundException(String message) {
        super("INTEGRATION_NOT_FOUND", message);
    }
}
