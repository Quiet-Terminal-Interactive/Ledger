package com.quietterminal.ledger.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.integration.IntegrationKind;
import com.quietterminal.ledger.integration.IntegrationRegistry;

@RestController
@RequestMapping("/integrations")
public class IntegrationController {

    private final IntegrationRegistry registry;

    public IntegrationController(IntegrationRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<IntegrationSummary> list() {
        return registry.all().stream()
                .map(i -> new IntegrationSummary(i.id(), i.displayName(), i.kind()))
                .sorted(Comparator.comparing(IntegrationSummary::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public record IntegrationSummary(String id, String displayName, IntegrationKind kind) {
    }
}
