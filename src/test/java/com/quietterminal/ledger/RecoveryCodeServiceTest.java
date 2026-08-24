package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.quietterminal.ledger.entity.RecoveryCode;
import com.quietterminal.ledger.error.RecoveryCodeInvalidException;
import com.quietterminal.ledger.repository.RecoveryCodeRepository;
import com.quietterminal.ledger.security.RecoveryCodeService;

class RecoveryCodeServiceTest {

    private final RecoveryCodeRepository repository = mock(RecoveryCodeRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final RecoveryCodeService service = new RecoveryCodeService(repository, passwordEncoder);
    private final UUID userId = UUID.randomUUID();

    @Test
    void regenerateReturnsTenUniquePlaintextCodesAndPersistsTheirHashes() {
        List<RecoveryCode> saved = new ArrayList<>();
        when(repository.saveAll(any())).thenAnswer(inv -> {
            List<RecoveryCode> batch = inv.getArgument(0);
            saved.addAll(batch);
            return batch;
        });

        List<String> codes = service.regenerate(userId);

        assertEquals(10, codes.size());
        assertEquals(10, saved.size());
        assertEquals(10, codes.stream().distinct().count());
        verify(repository).deleteByUserId(userId);
        for (int i = 0; i < codes.size(); i++) {
            assertTrue(passwordEncoder.matches(codes.get(i), saved.get(i).getCodeHash()));
        }
    }

    @Test
    void regeneratedCodesFollowTheFormattedPattern() {
        List<String> codes = service.regenerate(userId);

        for (String code : codes) {
            assertTrue(code.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"), "unexpected code format: " + code);
        }
    }

    @Test
    void redeemConsumesAMatchingCodeAndSavesIt() {
        RecoveryCode code = new RecoveryCode(userId, passwordEncoder.encode("ABCDE-FGHJK"));
        when(repository.findByUserIdAndUsedAtIsNull(userId)).thenReturn(List.of(code));

        service.redeem(userId, "ABCDE-FGHJK");

        assertTrue(code.isUsed());
        verify(repository).save(code);
    }

    @Test
    void redeemRejectsAWrongCode() {
        RecoveryCode code = new RecoveryCode(userId, passwordEncoder.encode("ABCDE-FGHJK"));
        when(repository.findByUserIdAndUsedAtIsNull(userId)).thenReturn(List.of(code));

        assertThrows(RecoveryCodeInvalidException.class, () -> service.redeem(userId, "WRONG-CODE1"));
        assertFalse(code.isUsed());
    }

    @Test
    void redeemRejectsWhenNoUnusedCodesExist() {
        when(repository.findByUserIdAndUsedAtIsNull(userId)).thenReturn(List.of());

        assertThrows(RecoveryCodeInvalidException.class, () -> service.redeem(userId, "ANYTHING1"));
    }

    @Test
    void statusReportsTotalAndRemainingSeparately() {
        RecoveryCode used = new RecoveryCode(userId, passwordEncoder.encode("USED0-CODE1"));
        used.markUsed();
        RecoveryCode unused = new RecoveryCode(userId, passwordEncoder.encode("FREE0-CODE1"));
        when(repository.findByUserId(userId)).thenReturn(List.of(used, unused));

        RecoveryCodeService.Status status = service.status(userId);

        assertEquals(2, status.total());
        assertEquals(1, status.remaining());
        assertNotNull(status.generatedAt());
    }

    @Test
    void statusWithNoCodesReturnsZeroesAndNullGeneratedAt() {
        when(repository.findByUserId(userId)).thenReturn(List.of());

        RecoveryCodeService.Status status = service.status(userId);

        assertEquals(0, status.total());
        assertEquals(0, status.remaining());
        assertNull(status.generatedAt());
    }

    @Test
    void revokeAllDeletesAllCodesForTheUser() {
        service.revokeAll(userId);

        verify(repository).deleteByUserId(userId);
    }
}
