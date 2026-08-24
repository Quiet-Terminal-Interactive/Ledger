package com.quietterminal.ledger.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quietterminal.ledger.entity.UserCredentials;

public interface UserCredentialsRepository extends JpaRepository<UserCredentials, UUID> {

    Optional<UserCredentials> findByUsername(String username);

    Optional<UserCredentials> findByUserId(UUID userId);

    boolean existsByUsername(String username);

    List<UserCredentials> findByUsernameContainingIgnoreCase(String usernameFragment);
}
