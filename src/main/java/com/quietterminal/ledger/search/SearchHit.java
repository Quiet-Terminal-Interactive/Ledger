package com.quietterminal.ledger.search;

import java.util.UUID;

import com.quietterminal.ledger.enums.SearchResultType;

public record SearchHit(SearchResultType type, UUID id, String title, String snippet) {
}
