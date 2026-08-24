package com.quietterminal.ledger.search;

import java.util.List;

public interface SearchService {

    List<SearchHit> search(String query);
}
