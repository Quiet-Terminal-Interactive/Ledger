package com.quietterminal.ledger.security;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.quietterminal.ledger.entity.RecoveryCode;
import com.quietterminal.ledger.error.RecoveryCodeInvalidException;
import com.quietterminal.ledger.repository.RecoveryCodeRepository;

@Service
public class RecoveryCodeService {

    private static final int CODE_COUNT = 10;
    private static final int CODE_CHARS = 10;
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final RecoveryCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public RecoveryCodeService(RecoveryCodeRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public List<String> regenerate(UUID userId) {
        repository.deleteByUserId(userId);

        List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
        List<RecoveryCode> toSave = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = generateCode();
            plaintextCodes.add(code);
            toSave.add(new RecoveryCode(userId, passwordEncoder.encode(code)));
        }
        repository.saveAll(toSave);

        return plaintextCodes;
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        List<RecoveryCode> codes = repository.findByUserId(userId);
        long remaining = codes.stream().filter(c -> !c.isUsed()).count();
        Instant generatedAt = codes.stream().map(RecoveryCode::getCreatedAt).min(Comparator.naturalOrder())
                .orElse(null);
        return new Status(codes.size(), (int) remaining, generatedAt);
    }

    /**
     * Redeems a recovery code, consuming it. Throws
     * {@link RecoveryCodeInvalidException} if the
     * code doesn't match any unused code for this user.
     */
    @Transactional
    public void redeem(UUID userId, String rawCode) {
        List<RecoveryCode> unused = repository.findByUserIdAndUsedAtIsNull(userId);
        RecoveryCode match = findMatch(unused, rawCode)
                .orElseThrow(() -> new RecoveryCodeInvalidException("Invalid or already-used recovery code."));
        match.markUsed();
        repository.save(match);
    }

    private Optional<RecoveryCode> findMatch(List<RecoveryCode> candidates, String rawCode) {
        return candidates.stream().filter(c -> passwordEncoder.matches(rawCode, c.getCodeHash())).findFirst();
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_CHARS);
        for (int i = 0; i < CODE_CHARS; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.substring(0, 5) + "-" + sb.substring(5);
    }

    public record Status(int total, int remaining, Instant generatedAt) {
    }
}
