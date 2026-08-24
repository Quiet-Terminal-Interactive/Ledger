package com.quietterminal.ledger.event;

import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.WikiPage;

public record WikiPageCreatedEvent(WikiPage page, User actor) {
}
