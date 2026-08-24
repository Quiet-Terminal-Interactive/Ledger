package com.quietterminal.ledger.notification;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.quietterminal.ledger.config.LedgerCurrency;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.enums.NamePattern;
import com.quietterminal.ledger.event.BudgetEntryCreatedEvent;
import com.quietterminal.ledger.event.TaskAssignedEvent;
import com.quietterminal.ledger.event.TaskBlockedEvent;
import com.quietterminal.ledger.event.TaskCompletedEvent;
import com.quietterminal.ledger.event.TaskCreatedEvent;
import com.quietterminal.ledger.event.UploadCreatedEvent;
import com.quietterminal.ledger.event.WikiPageCreatedEvent;

@Component
public class NotificationEventListener {

    private final List<Notifier> notifiers;
    private final LedgerCurrency currency;

    public NotificationEventListener(List<Notifier> notifiers, LedgerCurrency currency) {
        this.notifiers = notifiers;
        this.currency = currency;
    }

    @Async
    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        broadcast("🆕 **" + name(event.actor()) + "** created task \"" + event.task().getTitle() + "\"");
    }

    @Async
    @EventListener
    public void onTaskAssigned(TaskAssignedEvent event) {
        String assignees = event.assignees().stream().map(NotificationEventListener::name)
                .collect(Collectors.joining(", "));
        broadcast("👤 **" + name(event.actor()) + "** assigned \"" + event.task().getTitle() + "\" to " + assignees);
    }

    @Async
    @EventListener
    public void onTaskBlocked(TaskBlockedEvent event) {
        broadcast("🚧 **" + name(event.actor()) + "** marked \"" + event.task().getTitle() + "\" as blocked by \""
                + event.blocker().getTitle() + "\"");
    }

    @Async
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        broadcast("✅ **" + name(event.actor()) + "** completed \"" + event.task().getTitle() + "\"");
    }

    @Async
    @EventListener
    public void onUploadCreated(UploadCreatedEvent event) {
        broadcast("📁 **" + name(event.upload().getUploadedBy()) + "** uploaded \"" + event.upload().getFileName()
                + "\"");
    }

    @Async
    @EventListener
    public void onWikiPageCreated(WikiPageCreatedEvent event) {
        broadcast("📖 **" + name(event.actor()) + "** created wiki page \"" + event.page().getTitle() + "\"");
    }

    @Async
    @EventListener
    public void onBudgetEntryCreated(BudgetEntryCreatedEvent event) {
        broadcast("💸 **" + name(event.actor()) + "** added budget entry \"" + event.entry().getName() + "\" ("
                + currency.getSymbol() + event.entry().getAmount().toPlainString() + ")");
    }

    private void broadcast(String message) {
        notifiers.forEach(notifier -> notifier.notify(message));
    }

    private static String name(User user) {
        return user.getFullName(NamePattern.FirstLast);
    }
}
