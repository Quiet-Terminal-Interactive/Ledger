package com.quietterminal.ledger.error;

public class GitUpstreamException extends LedgerError {
    public GitUpstreamException(String message) {
        super("GIT_UPSTREAM_ERROR", message);
    }
}
