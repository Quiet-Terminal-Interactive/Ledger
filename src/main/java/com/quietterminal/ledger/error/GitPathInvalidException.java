package com.quietterminal.ledger.error;

public class GitPathInvalidException extends LedgerError {
    public GitPathInvalidException(String message) {
        super("GIT_PATH_INVALID", message);
    }
}
