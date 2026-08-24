package com.quietterminal.ledger.event;

import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.User;

public record TaskBlockedEvent(Task task, Task blocker, User actor) {
}
