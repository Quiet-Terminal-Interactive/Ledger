package com.quietterminal.ledger.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.quietterminal.ledger.entity.Session;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    boolean existsByIdAndExpiresAtAfter(UUID id, Instant now);

    List<Session> findByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId, Instant now);

    @Transactional
    long deleteByIdAndUserId(UUID id, UUID userId);

    @Transactional
    long deleteByUserId(UUID userId);
}
