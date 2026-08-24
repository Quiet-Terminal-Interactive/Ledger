package com.quietterminal.ledger.error;

public class GmailUpstreamException extends LedgerError {
    public GmailUpstreamException(String message) {
        super("GMAIL_UPSTREAM_ERROR", message);
    }
}
