package com.quietterminal.ledger.error;

public class UploadNotFoundException extends LedgerError {
    public UploadNotFoundException(String message) {
        super("UPLOAD_NOT_FOUND", message);
    }
}
