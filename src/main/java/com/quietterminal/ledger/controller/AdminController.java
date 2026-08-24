package com.quietterminal.ledger.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.enums.Permission;
import com.quietterminal.ledger.security.LedgerPrincipal;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/check")
    public AdminCheckResponse checkAdmin(@AuthenticationPrincipal LedgerPrincipal principal) {
        return new AdminCheckResponse(principal.hasPermission(Permission.USERS_MANAGE.name()),
                principal.hasPermission(Permission.ROLES_MANAGE.name()));
    }

    public record AdminCheckResponse(boolean canManageUsers, boolean canManageRoles) {
    }
}
