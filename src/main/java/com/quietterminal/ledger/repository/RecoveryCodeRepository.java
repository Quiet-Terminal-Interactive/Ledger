package com.quietterminal.ledger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quietterminal.ledger.entity.RecoveryCode;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    List<RecoveryCode> findByUserId(UUID userId);

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

    void deleteByUserId(UUID userId);
}
