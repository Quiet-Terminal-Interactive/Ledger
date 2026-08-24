package com.quietterminal.ledger.error;

public class UserNotFoundException extends LedgerError {
    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }
}
