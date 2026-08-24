package com.quietterminal.ledger.error;

public class TaskUpdateInvalidException extends LedgerError {
    public TaskUpdateInvalidException(String message) {
        super("TASK_UPDATE_INVALID", message);
    }
}
