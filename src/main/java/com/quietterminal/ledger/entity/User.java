package com.quietterminal.ledger.entity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.quietterminal.ledger.enums.NamePattern;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.UserCreationInvalidException;
import com.quietterminal.ledger.error.UserUpdateInvalidException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    private static final String NAME_PATTERN = "^[\\p{L}]+(?:[ '\\-][\\p{L}]+)*$";
    private static final String NAME_ERROR_MESSAGE = "Name cannot contain non-alphanumeric characters.";

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(cascade = CascadeType.MERGE)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    protected User() {
    }

    public User(String firstName, String lastName, Role role) {
        Objects.requireNonNull(role, "Role cannot be null.");

        validateNameOrThrow(firstName, "First name", UserCreationInvalidException::new);
        validateNameOrThrow(lastName, "Last name", UserCreationInvalidException::new);

        this.firstName = firstName;
        this.lastName = lastName;
        this.uuid = UUID.randomUUID();
        this.role = role;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName(NamePattern pattern) {
        return switch (pattern) {
            case FirstInitialLast ->
                String.valueOf(firstName.charAt(0)).toUpperCase() + ". " + capitaliseName(lastName);
            case FirstInitialLastNoSpace ->
                String.valueOf(firstName.charAt(0)).toUpperCase() + capitaliseName(lastName);
            case FirstInitialLastUsername ->
                String.valueOf(firstName.charAt(0)).toLowerCase() + "." + lastName.toLowerCase();
            case FirstLast -> capitaliseName(firstName) + " " + capitaliseName(lastName);
            case FirstLastInitial ->
                capitaliseName(firstName) + " " + String.valueOf(lastName.charAt(0)).toUpperCase() + ".";
            case FirstLastInitialNoSpace ->
                capitaliseName(firstName) + String.valueOf(lastName.charAt(0)).toUpperCase();
            case FirstLastLower -> firstName.toLowerCase() + " " + lastName.toLowerCase();
            case FirstLastNoSpace -> capitaliseName(firstName) + capitaliseName(lastName);
            case FirstLastUpper -> firstName.toUpperCase() + " " + lastName.toUpperCase();
            case Initials ->
                String.valueOf(firstName.charAt(0)).toUpperCase() + ". "
                        + String.valueOf(lastName.charAt(0)).toUpperCase() + ".";
            case InitialsNoSpace ->
                String.valueOf(firstName.charAt(0)).toUpperCase()
                        + String.valueOf(lastName.charAt(0)).toUpperCase();
            case LastFirst -> capitaliseName(lastName) + ", " + capitaliseName(firstName);
            case LastFirstInitial ->
                capitaliseName(lastName) + " " + String.valueOf(firstName.charAt(0)).toUpperCase() + ".";
            case LastFirstNoComma -> capitaliseName(lastName) + " " + capitaliseName(firstName);
            case LastFirstUsername -> lastName.toLowerCase() + "." + firstName.toLowerCase();
            case LastInitialFirst ->
                String.valueOf(lastName.charAt(0)).toUpperCase() + ". " + capitaliseName(firstName);
            case LastInitialFirstInitial ->
                String.valueOf(lastName.charAt(0)).toUpperCase() + ". "
                        + String.valueOf(firstName.charAt(0)).toUpperCase() + ".";
            case LastInitialFirstNoSpace ->
                String.valueOf(lastName.charAt(0)).toUpperCase() + capitaliseName(firstName);
            case UsernameCompact -> firstName.toLowerCase() + lastName.toLowerCase();
            case UsernameStyle -> firstName.toLowerCase() + "." + lastName.toLowerCase();
        };
    }

    public void setFirstName(String firstName) {
        validateNameOrThrow(firstName, "First name", UserUpdateInvalidException::new);
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        validateNameOrThrow(lastName, "Last name", UserUpdateInvalidException::new);
        this.lastName = lastName;
    }

    public UUID getUUID() {
        return uuid;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role, "Role cannot be null.");
    }

    private static void validateNameOrThrow(String name, String fieldLabel,
            Function<String, ? extends LedgerError> errorFactory) {
        if (name == null || !name.matches(NAME_PATTERN)) {
            throw errorFactory.apply(fieldLabel + ": " + NAME_ERROR_MESSAGE);
        }
    }

    private String capitaliseName(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return uuid != null && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}