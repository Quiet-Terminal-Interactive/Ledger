package com.quietterminal.ledger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quietterminal.ledger.entity.BudgetEntry;

public interface BudgetEntryRepository extends JpaRepository<BudgetEntry, UUID> {

    List<BudgetEntry> findAllByOrderByNameAsc();
}
