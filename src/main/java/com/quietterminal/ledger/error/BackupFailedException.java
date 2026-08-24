package com.quietterminal.ledger.error;

public class BackupFailedException extends LedgerError {
    public BackupFailedException(String message) {
        super("BACKUP_FAILED", message);
    }
}
