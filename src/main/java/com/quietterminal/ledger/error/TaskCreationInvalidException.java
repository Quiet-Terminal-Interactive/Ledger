package com.quietterminal.ledger.error;

public class TaskCreationInvalidException extends LedgerError {
    public TaskCreationInvalidException(String message) {
        super("TASK_CREATION_INVALID", message);
    }
}
