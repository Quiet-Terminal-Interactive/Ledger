package com.quietterminal.ledger.error;

public class LedgerError extends RuntimeException {

    private final String code;

    public LedgerError(String code, String message) {
        super(message);
        this.code = code;
    }

    public LedgerError(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}