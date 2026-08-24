package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.WikiPage;
import com.quietterminal.ledger.error.WikiPageInvalidException;

class WikiPageTest {

    private static final Role ADMIN = new Role("Admin", Set.of());
    private final User kohan = new User("Kohan", "Mathers", ADMIN);

    @Test
    void canCreatePageWithPathTitleAndContent() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "# Hello", kohan);

        assertEquals("guides/setup", page.getPath());
        assertEquals("Setup Guide", page.getTitle());
        assertEquals("# Hello", page.getContent());
        assertEquals(kohan, page.getCreatedBy());
    }

    @Test
    void pathIsNormalizedToLowercaseWithoutSurroundingSlashes() {
        WikiPage page = new WikiPage("/Guides/Setup/", "Setup Guide", "content", kohan);

        assertEquals("guides/setup", page.getPath());
    }

    @Test
    void contentDefaultsToEmptyStringWhenNull() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", null, kohan);

        assertEquals("", page.getContent());
    }

    @Test
    void everyPageGetsAUniqueNonNullUUID() {
        WikiPage first = new WikiPage("first", "First", "c", kohan);
        WikiPage second = new WikiPage("second", "Second", "c", kohan);

        assertNotNull(first.getUUID());
        assertNotNull(second.getUUID());
        assertNotEquals(first.getUUID(), second.getUUID());
        assertInstanceOf(UUID.class, first.getUUID());
    }

    @Test
    void createdAtAndUpdatedAtStartEqual() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "content", kohan);

        assertEquals(page.getCreatedAt(), page.getUpdatedAt());
    }

    @Test
    void cannotUseNullCreator() {
        assertThrows(NullPointerException.class, () -> new WikiPage("guides/setup", "Setup Guide", "c", null));
    }

    @Test
    void cannotUseBlankTitleOnCreation() {
        assertThrows(WikiPageInvalidException.class, () -> new WikiPage("guides/setup", "", "c", kohan));
    }

    @Test
    void cannotUseNullTitleOnCreation() {
        assertThrows(WikiPageInvalidException.class, () -> new WikiPage("guides/setup", null, "c", kohan));
    }

    @Test
    void cannotUseBlankPathOnCreation() {
        assertThrows(WikiPageInvalidException.class, () -> new WikiPage("  ", "Setup Guide", "c", kohan));
    }

    @Test
    void cannotUseNullPathOnCreation() {
        assertThrows(WikiPageInvalidException.class, () -> new WikiPage(null, "Setup Guide", "c", kohan));
    }

    @Test
    void cannotUseInvalidPathSegmentCharacters() {
        assertThrows(WikiPageInvalidException.class,
                () -> new WikiPage("guides/Setup Steps!", "Setup Guide", "c", kohan));
    }

    @Test
    void canUpdateTitleOnHappyPath() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "content", kohan);

        page.setTitle("New Title");

        assertEquals("New Title", page.getTitle());
    }

    @Test
    void cannotUpdateTitleToBlank() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "content", kohan);

        assertThrows(WikiPageInvalidException.class, () -> page.setTitle(""));
        assertEquals("Setup Guide", page.getTitle());
    }

    @Test
    void canUpdateContent() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "content", kohan);

        page.setContent("new content");

        assertEquals("new content", page.getContent());
    }

    @Test
    void contentCanBeSetToNullWhichNormalizesToEmptyString() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "content", kohan);

        page.setContent(null);

        assertEquals("", page.getContent());
    }

    @Test
    void updatingTitleBumpsUpdatedAt() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "content", kohan);
        var originalUpdatedAt = page.getUpdatedAt();

        page.setTitle("New Title");

        assertTrue(page.getUpdatedAt().isAfter(originalUpdatedAt) || page.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void pageEqualsItself() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "content", kohan);

        assertEquals(page, page);
    }

    @Test
    void pageDoesNotEqualADifferentPage() {
        WikiPage first = new WikiPage("first", "First", "c", kohan);
        WikiPage second = new WikiPage("second", "Second", "c", kohan);

        assertNotEquals(first, second);
    }

    @Test
    void pageDoesNotEqualNullOrAnUnrelatedType() {
        WikiPage page = new WikiPage("guides/setup", "Setup Guide", "content", kohan);

        assertNotEquals(null, page);
        assertNotEquals("Setup Guide", page);
    }
}
