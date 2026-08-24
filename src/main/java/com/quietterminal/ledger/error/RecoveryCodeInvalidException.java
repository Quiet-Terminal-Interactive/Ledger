package com.quietterminal.ledger.error;

public class RecoveryCodeInvalidException extends LedgerError {
    public RecoveryCodeInvalidException(String message) {
        super("RECOVERY_CODE_INVALID", message);
    }
}
