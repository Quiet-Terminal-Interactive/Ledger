package com.quietterminal.ledger.error;

public class UserUpdateInvalidException extends LedgerError {
    public UserUpdateInvalidException(String message) {
        super("USER_UPDATE_INVALID", message);
    }
}