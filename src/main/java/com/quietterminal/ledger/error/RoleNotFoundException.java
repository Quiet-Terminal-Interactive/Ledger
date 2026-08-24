package com.quietterminal.ledger.error;

public class RoleNotFoundException extends LedgerError {
    public RoleNotFoundException(String message) {
        super("ROLE_NOT_FOUND", message);
    }
}
