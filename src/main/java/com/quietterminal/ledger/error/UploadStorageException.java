package com.quietterminal.ledger.error;

public class UploadStorageException extends LedgerError {
    public UploadStorageException(String message) {
        super("UPLOAD_STORAGE_ERROR", message);
    }
}
