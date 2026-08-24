package com.quietterminal.ledger.error;

public class TaskNotFoundException extends LedgerError {
    public TaskNotFoundException(String message) {
        super("TASK_NOT_FOUND", message);
    }
}
