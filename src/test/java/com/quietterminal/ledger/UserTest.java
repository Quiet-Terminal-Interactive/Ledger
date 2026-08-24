package com.quietterminal.ledger;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.enums.NamePattern;
import com.quietterminal.ledger.error.UserCreationInvalidException;
import com.quietterminal.ledger.error.UserUpdateInvalidException;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final Role MEMBER = new Role("Member", Set.of());
    private static final Role ADMIN = new Role("Admin", Set.of());

    @Test
    void canCreateUsersOnHappyPath() {
        User user = new User("John", "Smith", MEMBER);

        assertEquals("John", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals(MEMBER, user.getRole());
    }

    @Test
    void cannotUseNullRoleOnUserCreation() {
        assertThrows(NullPointerException.class, () -> new User("John", "Smith", null));
    }

    @Test
    void cannotUseNonValidNameOnUserCreation() {
        assertThrows(UserCreationInvalidException.class, () -> new User("J/o/h/n", "Smith", MEMBER));
    }

    @Test
    void cannotUseNullFirstNameOnUserCreation() {
        assertThrows(UserCreationInvalidException.class, () -> new User(null, "Smith", MEMBER));
    }

    @Test
    void cannotUseNullLastNameOnUserCreation() {
        assertThrows(UserCreationInvalidException.class, () -> new User("John", null, MEMBER));
    }

    @Test
    void cannotUseInvalidLastNameOnUserCreation() {
        assertThrows(UserCreationInvalidException.class, () -> new User("John", "Sm1th", MEMBER));
    }

    @Test
    void cannotUseEmptyNameOnUserCreation() {
        assertThrows(UserCreationInvalidException.class, () -> new User("", "Smith", MEMBER));
    }

    @ParameterizedTest
    @ValueSource(strings = { "John", "Mary Jane", "O'Brien", "Anne-Marie", "Renée" })
    void acceptsValidNamesOnUserCreation(String name) {
        assertDoesNotThrow(() -> new User(name, "Smith", MEMBER));
    }

    @ParameterizedTest
    @ValueSource(strings = { "John1", "John_", "John.", "-John", "'John", " John", "John " })
    void rejectsInvalidNamesOnUserCreation(String name) {
        assertThrows(UserCreationInvalidException.class, () -> new User(name, "Smith", MEMBER));
    }

    @Test
    void everyUserGetsAUniqueNonNullUUID() {
        User first = new User("John", "Smith", MEMBER);
        User second = new User("John", "Smith", MEMBER);

        assertNotNull(first.getUUID());
        assertNotNull(second.getUUID());
        assertNotEquals(first.getUUID(), second.getUUID());
        assertInstanceOf(UUID.class, first.getUUID());
    }

    @Test
    void canUpdateFirstNameOnHappyPath() {
        User user = new User("John", "Smith", MEMBER);

        user.setFirstName("Jane");

        assertEquals("Jane", user.getFirstName());
    }

    @Test
    void canUpdateLastNameOnHappyPath() {
        User user = new User("John", "Smith", MEMBER);

        user.setLastName("Doe");

        assertEquals("Doe", user.getLastName());
    }

    @Test
    void cannotUpdateFirstNameToInvalidValue() {
        User user = new User("John", "Smith", MEMBER);

        assertThrows(UserUpdateInvalidException.class, () -> user.setFirstName("J0hn"));
        assertEquals("John", user.getFirstName());
    }

    @Test
    void cannotUpdateFirstNameToNull() {
        User user = new User("John", "Smith", MEMBER);

        assertThrows(UserUpdateInvalidException.class, () -> user.setFirstName(null));
    }

    @Test
    void cannotUpdateLastNameToInvalidValue() {
        User user = new User("John", "Smith", MEMBER);

        assertThrows(UserUpdateInvalidException.class, () -> user.setLastName("Sm1th"));
        assertEquals("Smith", user.getLastName());
    }

    @Test
    void cannotUpdateLastNameToNull() {
        User user = new User("John", "Smith", MEMBER);

        assertThrows(UserUpdateInvalidException.class, () -> user.setLastName(null));
    }

    @Test
    void canUpdateRoleOnHappyPath() {
        User user = new User("John", "Smith", MEMBER);

        user.setRole(ADMIN);

        assertEquals(ADMIN, user.getRole());
    }

    @Test
    void cannotUpdateRoleToNull() {
        User user = new User("John", "Smith", MEMBER);

        assertThrows(NullPointerException.class, () -> user.setRole(null));
        assertEquals(MEMBER, user.getRole());
    }

    @Test
    void fullNameForFirstInitialLast() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("J. Smith", user.getFullName(NamePattern.FirstInitialLast));
    }

    @Test
    void fullNameForFirstInitialLastNoSpace() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("JSmith", user.getFullName(NamePattern.FirstInitialLastNoSpace));
    }

    @Test
    void fullNameForFirstInitialLastUsername() {
        User user = new User("John", "Smith", MEMBER);

        assertEquals("j.smith", user.getFullName(NamePattern.FirstInitialLastUsername));
    }

    @Test
    void fullNameForFirstLast() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("John Smith", user.getFullName(NamePattern.FirstLast));
    }

    @Test
    void fullNameForFirstLastInitial() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("John S.", user.getFullName(NamePattern.FirstLastInitial));
    }

    @Test
    void fullNameForFirstLastInitialNoSpace() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("JohnS", user.getFullName(NamePattern.FirstLastInitialNoSpace));
    }

    @Test
    void fullNameForFirstLastLower() {
        User user = new User("John", "Smith", MEMBER);

        assertEquals("john smith", user.getFullName(NamePattern.FirstLastLower));
    }

    @Test
    void fullNameForFirstLastNoSpace() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("JohnSmith", user.getFullName(NamePattern.FirstLastNoSpace));
    }

    @Test
    void fullNameForFirstLastUpper() {
        User user = new User("John", "Smith", MEMBER);

        assertEquals("JOHN SMITH", user.getFullName(NamePattern.FirstLastUpper));
    }

    @Test
    void fullNameForInitials() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("J. S.", user.getFullName(NamePattern.Initials));
    }

    @Test
    void fullNameForInitialsNoSpace() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("JS", user.getFullName(NamePattern.InitialsNoSpace));
    }

    @Test
    void fullNameForLastFirst() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("Smith, John", user.getFullName(NamePattern.LastFirst));
    }

    @Test
    void fullNameForLastFirstInitial() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("Smith J.", user.getFullName(NamePattern.LastFirstInitial));
    }

    @Test
    void fullNameForLastFirstNoComma() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("Smith John", user.getFullName(NamePattern.LastFirstNoComma));
    }

    @Test
    void fullNameForLastFirstUsername() {
        User user = new User("John", "Smith", MEMBER);

        assertEquals("smith.john", user.getFullName(NamePattern.LastFirstUsername));
    }

    @Test
    void fullNameForLastInitialFirst() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("S. John", user.getFullName(NamePattern.LastInitialFirst));
    }

    @Test
    void fullNameForLastInitialFirstInitial() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("S. J.", user.getFullName(NamePattern.LastInitialFirstInitial));
    }

    @Test
    void fullNameForLastInitialFirstNoSpace() {
        User user = new User("john", "smith", MEMBER);

        assertEquals("SJohn", user.getFullName(NamePattern.LastInitialFirstNoSpace));
    }

    @Test
    void fullNameForUsernameCompact() {
        User user = new User("John", "Smith", MEMBER);

        assertEquals("johnsmith", user.getFullName(NamePattern.UsernameCompact));
    }

    @Test
    void fullNameForUsernameStyle() {
        User user = new User("John", "Smith", MEMBER);

        assertEquals("john.smith", user.getFullName(NamePattern.UsernameStyle));
    }

    @Test
    void capitaliseNameOnlyUppercasesTheFirstCharacterOfTheWholeString() {
        User user = new User("mary-jane", "van-buren", MEMBER);

        assertEquals("Mary-jane Van-buren", user.getFullName(NamePattern.FirstLast));
    }
}
