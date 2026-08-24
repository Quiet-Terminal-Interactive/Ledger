package com.quietterminal.ledger.error;

public class UserForbiddenException extends LedgerError {
    public UserForbiddenException(String message) {
        super("USER_FORBIDDEN", message);
    }
}
