package com.quietterminal.ledger.error;

public class GitResourceNotFoundException extends LedgerError {
    public GitResourceNotFoundException(String message) {
        super("GIT_RESOURCE_NOT_FOUND", message);
    }
}
