package com.quietterminal.ledger.error;

public class WikiPageInvalidException extends LedgerError {
    public WikiPageInvalidException(String message) {
        super("WIKI_PAGE_INVALID", message);
    }
}
