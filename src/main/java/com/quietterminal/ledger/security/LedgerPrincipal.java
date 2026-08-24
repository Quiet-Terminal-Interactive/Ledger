package com.quietterminal.ledger.security;

import java.util.Set;
import java.util.UUID;

public record LedgerPrincipal(UUID userId, String username, UUID sessionId, String role, Set<String> permissions) {
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
