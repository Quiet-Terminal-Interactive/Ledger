package com.quietterminal.ledger.error;

public class BudgetEntryInvalidException extends LedgerError {
    public BudgetEntryInvalidException(String message) {
        super("BUDGET_ENTRY_INVALID", message);
    }
}
