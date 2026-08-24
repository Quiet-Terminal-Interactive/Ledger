package com.quietterminal.ledger;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.config.LedgerCurrency;
import com.quietterminal.ledger.enums.BudgetEntryType;
import com.quietterminal.ledger.entity.BudgetEntry;
import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.Upload;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.WikiPage;
import com.quietterminal.ledger.event.BudgetEntryCreatedEvent;
import com.quietterminal.ledger.event.TaskAssignedEvent;
import com.quietterminal.ledger.event.TaskBlockedEvent;
import com.quietterminal.ledger.event.TaskCompletedEvent;
import com.quietterminal.ledger.event.TaskCreatedEvent;
import com.quietterminal.ledger.event.UploadCreatedEvent;
import com.quietterminal.ledger.event.WikiPageCreatedEvent;
import com.quietterminal.ledger.notification.NotificationEventListener;
import com.quietterminal.ledger.notification.Notifier;

class NotificationEventListenerTest {

    private static final Role ADMIN = new Role("Admin", Set.of());
    private static final Role MEMBER = new Role("Member", Set.of());
    private final User kohan = new User("Kohan", "Mathers", ADMIN);
    private final User nat = new User("Nat", "Someone", MEMBER);
    private final LedgerCurrency usd = new LedgerCurrency("USD");

    @Test
    void taskCreatedNotifiesWithActorAndTitle() {
        Notifier notifier = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(notifier), usd);
        Task task = new Task("Animate bear", "desc", Set.of(), LocalDate.now());

        listener.onTaskCreated(new TaskCreatedEvent(task, kohan));

        verify(notifier).notify(contains("Animate bear"));
    }

    @Test
    void taskAssignedNotifiesWithAssigneeName() {
        Notifier notifier = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(notifier), usd);
        Task task = new Task("Animate bear", "desc", Set.of(), LocalDate.now());

        listener.onTaskAssigned(new TaskAssignedEvent(task, Set.of(nat), kohan));

        verify(notifier).notify(contains("Nat"));
    }

    @Test
    void taskBlockedNotifiesWithBothTaskTitles() {
        Notifier notifier = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(notifier), usd);
        Task task = new Task("Animate bear", "desc", Set.of(), LocalDate.now());
        Task blocker = new Task("Bear variations", "desc", Set.of(), LocalDate.now());

        listener.onTaskBlocked(new TaskBlockedEvent(task, blocker, kohan));

        verify(notifier).notify(contains("Bear variations"));
    }

    @Test
    void taskCompletedNotifiesWithTitle() {
        Notifier notifier = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(notifier), usd);
        Task task = new Task("Animate bear", "desc", Set.of(), LocalDate.now());

        listener.onTaskCompleted(new TaskCompletedEvent(task, kohan));

        verify(notifier).notify(contains("Animate bear"));
    }

    @Test
    void uploadCreatedNotifiesWithFileName() {
        Notifier notifier = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(notifier), usd);
        Upload upload = new Upload("bear-ref.png", "image/png", 1024L, "key", "bucket", "", kohan);

        listener.onUploadCreated(new UploadCreatedEvent(upload));

        verify(notifier).notify(contains("bear-ref.png"));
    }

    @Test
    void wikiPageCreatedNotifiesWithTitle() {
        Notifier notifier = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(notifier), usd);
        WikiPage page = new WikiPage("guides/bear-setup", "Bear Setup Guide", "content", kohan);

        listener.onWikiPageCreated(new WikiPageCreatedEvent(page, kohan));

        verify(notifier).notify(contains("Bear Setup Guide"));
    }

    @Test
    void budgetEntryCreatedNotifiesWithNameAndAmount() {
        Notifier notifier = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(notifier), usd);
        BudgetEntry entry = new BudgetEntry("Discord Nitro", "Server boosts", new BigDecimal("9.99"),
                BudgetEntryType.EXPENSE, kohan);

        listener.onBudgetEntryCreated(new BudgetEntryCreatedEvent(entry, kohan));

        verify(notifier).notify(contains("Discord Nitro"));
        verify(notifier).notify(contains("$9.99"));
    }

    @Test
    void budgetEntryCreatedUsesTheConfiguredCurrencySymbol() {
        Notifier notifier = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(notifier),
                new LedgerCurrency("GBP"));
        BudgetEntry entry = new BudgetEntry("VPS", "Hosting", new BigDecimal("12.50"), BudgetEntryType.EXPENSE,
                kohan);

        listener.onBudgetEntryCreated(new BudgetEntryCreatedEvent(entry, kohan));

        verify(notifier).notify(contains("£12.50"));
    }

    @Test
    void broadcastsToEveryConfiguredNotifier() {
        Notifier discord = mock(Notifier.class);
        Notifier slack = mock(Notifier.class);
        NotificationEventListener listener = new NotificationEventListener(List.of(discord, slack), usd);
        Task task = new Task("Animate bear", "desc", Set.of(), LocalDate.now());

        listener.onTaskCreated(new TaskCreatedEvent(task, kohan));

        verify(discord).notify(contains("Animate bear"));
        verify(slack).notify(contains("Animate bear"));
    }

    @Test
    void noNotifiersConfiguredIsANoOp() {
        NotificationEventListener listener = new NotificationEventListener(List.of(), usd);
        Task task = new Task("Animate bear", "desc", Set.of(), LocalDate.now());

        listener.onTaskCreated(new TaskCreatedEvent(task, kohan));
    }
}
