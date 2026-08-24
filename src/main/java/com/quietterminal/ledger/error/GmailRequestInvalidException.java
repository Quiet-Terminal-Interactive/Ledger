package com.quietterminal.ledger.error;

public class GmailRequestInvalidException extends LedgerError {
    public GmailRequestInvalidException(String message) {
        super("GMAIL_REQUEST_INVALID", message);
    }
}
