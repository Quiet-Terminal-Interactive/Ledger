package com.quietterminal.ledger.error;

public class UserDeletionConflictException extends LedgerError {
    public UserDeletionConflictException(String message) {
        super("USER_DELETION_CONFLICT", message);
    }
}
