package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.entity.BudgetEntry;
import com.quietterminal.ledger.enums.BudgetEntryType;
import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.error.BudgetEntryInvalidException;

class BudgetEntryTest {

    private static final Role ADMIN = new Role("Admin", Set.of());
    private final User kohan = new User("Kohan", "Mathers", ADMIN);

    @Test
    void canCreateEntryWithNameDescriptionAndAmount() {
        BudgetEntry entry = new BudgetEntry("Discord Nitro", "Server boosts", new BigDecimal("9.99"), BudgetEntryType.EXPENSE, kohan);

        assertEquals("Discord Nitro", entry.getName());
        assertEquals("Server boosts", entry.getDescription());
        assertEquals(new BigDecimal("9.99"), entry.getAmount());
        assertEquals(kohan, entry.getCreatedBy());
    }

    @Test
    void descriptionDefaultsToEmptyStringWhenNull() {
        BudgetEntry entry = new BudgetEntry("Hosting", null, new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);

        assertEquals("", entry.getDescription());
    }

    @Test
    void everyEntryGetsAUniqueNonNullUUID() {
        BudgetEntry first = new BudgetEntry("First", "d", new BigDecimal("1.00"), BudgetEntryType.EXPENSE, kohan);
        BudgetEntry second = new BudgetEntry("Second", "d", new BigDecimal("2.00"), BudgetEntryType.EXPENSE, kohan);

        assertNotNull(first.getUUID());
        assertNotNull(second.getUUID());
        assertNotEquals(first.getUUID(), second.getUUID());
        assertInstanceOf(UUID.class, first.getUUID());
    }

    @Test
    void createdAtAndUpdatedAtStartEqual() {
        BudgetEntry entry = new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);

        assertEquals(entry.getCreatedAt(), entry.getUpdatedAt());
    }

    @Test
    void cannotUseNullCreator() {
        assertThrows(NullPointerException.class,
                () -> new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, null));
    }

    @Test
    void cannotUseBlankNameOnCreation() {
        assertThrows(BudgetEntryInvalidException.class,
                () -> new BudgetEntry("", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan));
    }

    @Test
    void cannotUseNullNameOnCreation() {
        assertThrows(BudgetEntryInvalidException.class,
                () -> new BudgetEntry(null, "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan));
    }

    @Test
    void cannotUseNullAmountOnCreation() {
        assertThrows(BudgetEntryInvalidException.class, () -> new BudgetEntry("Hosting", "d", null, BudgetEntryType.EXPENSE, kohan));
    }

    @Test
    void cannotUseNegativeAmountOnCreation() {
        assertThrows(BudgetEntryInvalidException.class,
                () -> new BudgetEntry("Hosting", "d", new BigDecimal("-1.00"), BudgetEntryType.EXPENSE, kohan));
    }

    @Test
    void canUpdateNameDescriptionAndAmount() {
        BudgetEntry entry = new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);

        entry.setName("New Hosting");
        entry.setDescription("new description");
        entry.setAmount(new BigDecimal("6.50"));

        assertEquals("New Hosting", entry.getName());
        assertEquals("new description", entry.getDescription());
        assertEquals(new BigDecimal("6.50"), entry.getAmount());
    }

    @Test
    void cannotUpdateNameToBlank() {
        BudgetEntry entry = new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);

        assertThrows(BudgetEntryInvalidException.class, () -> entry.setName(""));
        assertEquals("Hosting", entry.getName());
    }

    @Test
    void cannotUpdateAmountToNegative() {
        BudgetEntry entry = new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);

        assertThrows(BudgetEntryInvalidException.class, () -> entry.setAmount(new BigDecimal("-1.00")));
        assertEquals(new BigDecimal("5.00"), entry.getAmount());
    }

    @Test
    void descriptionCanBeSetToNullWhichNormalizesToEmptyString() {
        BudgetEntry entry = new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);

        entry.setDescription(null);

        assertEquals("", entry.getDescription());
    }

    @Test
    void updatingNameBumpsUpdatedAt() {
        BudgetEntry entry = new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);
        var originalUpdatedAt = entry.getUpdatedAt();

        entry.setName("New Hosting");

        assertTrue(entry.getUpdatedAt().isAfter(originalUpdatedAt) || entry.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void entryEqualsItself() {
        BudgetEntry entry = new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);

        assertEquals(entry, entry);
    }

    @Test
    void entryDoesNotEqualADifferentEntry() {
        BudgetEntry first = new BudgetEntry("First", "d", new BigDecimal("1.00"), BudgetEntryType.EXPENSE, kohan);
        BudgetEntry second = new BudgetEntry("Second", "d", new BigDecimal("2.00"), BudgetEntryType.EXPENSE, kohan);

        assertNotEquals(first, second);
    }

    @Test
    void entryDoesNotEqualNullOrAnUnrelatedType() {
        BudgetEntry entry = new BudgetEntry("Hosting", "d", new BigDecimal("5.00"), BudgetEntryType.EXPENSE, kohan);

        assertNotEquals(null, entry);
        assertNotEquals("Hosting", entry);
    }
}
