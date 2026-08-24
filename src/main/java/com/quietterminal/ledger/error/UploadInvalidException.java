package com.quietterminal.ledger.error;

public class UploadInvalidException extends LedgerError {
    public UploadInvalidException(String message) {
        super("UPLOAD_INVALID", message);
    }
}
