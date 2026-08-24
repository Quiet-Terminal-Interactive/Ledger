package com.quietterminal.ledger.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quietterminal.ledger.error.GmailMessageNotFoundException;
import com.quietterminal.ledger.error.GmailRequestInvalidException;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.gmail.GmailService;
import com.quietterminal.ledger.gmail.GmailService.MessageDetail;
import com.quietterminal.ledger.gmail.GmailService.MessageListPage;

@RestController
@RequestMapping("/email")
@ConditionalOnProperty(name = "ledger.gmail.enabled", havingValue = "true")
public class EmailController {

    private final GmailService gmailService;

    public EmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    @GetMapping("/messages")
    public MessageListPage listMessages(@RequestParam(name = "maxResults", defaultValue = "20") int maxResults,
            @RequestParam(name = "pageToken", required = false) String pageToken) {
        return gmailService.listMessages(maxResults, pageToken);
    }

    @GetMapping("/messages/{id}")
    public MessageDetail getMessage(@PathVariable("id") String id) {
        return gmailService.getMessage(id);
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status = e instanceof GmailMessageNotFoundException ? HttpStatus.NOT_FOUND
                : e instanceof GmailRequestInvalidException ? HttpStatus.BAD_REQUEST
                        : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(e.getMessage());
    }
}
