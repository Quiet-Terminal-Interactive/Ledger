package com.quietterminal.ledger.config;

import java.util.Currency;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LedgerCurrency {

    private final Currency currency;

    public LedgerCurrency(@Value("${ledger.budget.currency:USD}") String code) {
        try {
            this.currency = Currency.getInstance(code.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ledger.budget.currency (env LEDGER_BUDGET_CURRENCY) \"" + code
                            + "\" is not a recognized ISO 4217 currency code.",
                    e);
        }
    }

    public String getCode() {
        return currency.getCurrencyCode();
    }

    public String getSymbol() {
        return currency.getSymbol(Locale.US);
    }
}
