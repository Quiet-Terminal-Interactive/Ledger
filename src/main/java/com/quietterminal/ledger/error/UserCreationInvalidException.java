package com.quietterminal.ledger.error;

public class UserCreationInvalidException extends LedgerError {
    public UserCreationInvalidException(String message) {
        super("USER_CREATION_INVALID", message);
    }
}