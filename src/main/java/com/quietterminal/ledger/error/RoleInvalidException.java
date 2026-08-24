package com.quietterminal.ledger.error;

public class RoleInvalidException extends LedgerError {
    public RoleInvalidException(String message) {
        super("ROLE_INVALID", message);
    }
}
