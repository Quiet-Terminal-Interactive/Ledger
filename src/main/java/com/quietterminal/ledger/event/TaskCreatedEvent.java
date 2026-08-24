package com.quietterminal.ledger.event;

import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.User;

public record TaskCreatedEvent(Task task, User actor) {
}
