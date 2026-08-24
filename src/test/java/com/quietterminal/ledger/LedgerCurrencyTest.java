package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.config.LedgerCurrency;

class LedgerCurrencyTest {

    @Test
    void resolvesCodeAndSymbolForUsd() {
        LedgerCurrency currency = new LedgerCurrency("USD");

        assertEquals("USD", currency.getCode());
        assertEquals("$", currency.getSymbol());
    }

    @Test
    void resolvesCodeAndSymbolForGbp() {
        LedgerCurrency currency = new LedgerCurrency("GBP");

        assertEquals("GBP", currency.getCode());
        assertEquals("£", currency.getSymbol());
    }

    @Test
    void resolvesCodeAndSymbolForEur() {
        LedgerCurrency currency = new LedgerCurrency("EUR");

        assertEquals("EUR", currency.getCode());
        assertEquals("€", currency.getSymbol());
    }

    @Test
    void normalizesLowercaseAndWhitespace() {
        LedgerCurrency currency = new LedgerCurrency(" gbp ");

        assertEquals("GBP", currency.getCode());
    }

    @Test
    void rejectsUnrecognizedCurrencyCode() {
        assertThrows(IllegalStateException.class, () -> new LedgerCurrency("NOT_A_CURRENCY"));
    }
}
