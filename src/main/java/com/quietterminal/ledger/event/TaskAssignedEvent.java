package com.quietterminal.ledger.event;

import java.util.Set;

import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.User;

public record TaskAssignedEvent(Task task, Set<User> assignees, User actor) {
}
