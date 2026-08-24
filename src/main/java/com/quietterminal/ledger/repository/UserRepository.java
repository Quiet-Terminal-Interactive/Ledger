package com.quietterminal.ledger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quietterminal.ledger.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByRole_Id(UUID roleId);
}
