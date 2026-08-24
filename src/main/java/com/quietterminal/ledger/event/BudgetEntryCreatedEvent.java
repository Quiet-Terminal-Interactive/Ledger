package com.quietterminal.ledger.event;

import com.quietterminal.ledger.entity.BudgetEntry;
import com.quietterminal.ledger.entity.User;

public record BudgetEntryCreatedEvent(BudgetEntry entry, User actor) {
}
