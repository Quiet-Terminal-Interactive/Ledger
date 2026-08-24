package com.quietterminal.ledger.error;

public class GmailMessageNotFoundException extends LedgerError {
    public GmailMessageNotFoundException(String message) {
        super("GMAIL_MESSAGE_NOT_FOUND", message);
    }
}
