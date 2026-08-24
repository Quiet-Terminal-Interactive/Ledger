package com.quietterminal.ledger.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrandingController {

    private final String logoUrl;

    public BrandingController(@Value("${ledger.branding.logo-url:}") String logoUrl) {
        this.logoUrl = logoUrl;
    }

    @GetMapping("/branding")
    public BrandingResponse branding() {
        return new BrandingResponse(logoUrl == null || logoUrl.isBlank() ? null : logoUrl);
    }

    public record BrandingResponse(String logoUrl) {
    }
}
