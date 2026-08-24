package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.entity.RecoveryCode;
import com.quietterminal.ledger.error.RecoveryCodeInvalidException;

class RecoveryCodeTest {

    @Test
    void newlyCreatedCodeIsUnused() {
        RecoveryCode code = new RecoveryCode(UUID.randomUUID(), "hash");

        assertFalse(code.isUsed());
        assertNull(code.getUsedAt());
        assertNotNull(code.getCreatedAt());
        assertNotNull(code.getId());
    }

    @Test
    void markUsedSetsUsedAt() {
        RecoveryCode code = new RecoveryCode(UUID.randomUUID(), "hash");

        code.markUsed();

        assertTrue(code.isUsed());
        assertNotNull(code.getUsedAt());
    }

    @Test
    void cannotMarkAnAlreadyUsedCodeAsUsedAgain() {
        RecoveryCode code = new RecoveryCode(UUID.randomUUID(), "hash");
        code.markUsed();

        assertThrows(RecoveryCodeInvalidException.class, code::markUsed);
    }

    @Test
    void cannotCreateWithNullUserId() {
        assertThrows(NullPointerException.class, () -> new RecoveryCode(null, "hash"));
    }

    @Test
    void cannotCreateWithNullCodeHash() {
        assertThrows(NullPointerException.class, () -> new RecoveryCode(UUID.randomUUID(), null));
    }
}
