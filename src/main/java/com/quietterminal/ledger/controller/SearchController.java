package com.quietterminal.ledger.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.enums.Permission;
import com.quietterminal.ledger.search.SearchHit;
import com.quietterminal.ledger.search.SearchService;
import com.quietterminal.ledger.security.LedgerPrincipal;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<SearchHit> search(@RequestParam("q") String query, @AuthenticationPrincipal LedgerPrincipal principal) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return searchService.search(query.strip()).stream()
                .filter(hit -> isVisible(hit, principal))
                .toList();
    }

    private static boolean isVisible(SearchHit hit, LedgerPrincipal principal) {
        return switch (hit.type()) {
            case TASK -> principal.hasPermission(Permission.TASKS_READ.name());
            case WIKI_PAGE -> principal.hasPermission(Permission.WIKI_READ.name());
            case UPLOAD -> principal.hasPermission(Permission.FILES_READ.name());
        };
    }
}
