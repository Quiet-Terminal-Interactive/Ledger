package com.quietterminal.ledger.integration;

public interface Integration {

    String id();

    String displayName();

    IntegrationKind kind();
}
