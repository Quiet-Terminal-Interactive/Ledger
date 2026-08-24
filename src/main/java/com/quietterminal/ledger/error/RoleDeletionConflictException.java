package com.quietterminal.ledger.error;

public class RoleDeletionConflictException extends LedgerError {
    public RoleDeletionConflictException(String message) {
        super("ROLE_DELETION_CONFLICT", message);
    }
}
