package com.quietterminal.ledger.notification;

import com.quietterminal.ledger.integration.Integration;
import com.quietterminal.ledger.integration.IntegrationKind;

public interface Notifier extends Integration {

    @Override
    default IntegrationKind kind() {
        return IntegrationKind.OUTBOUND_WEBHOOK;
    }

    void notify(String message);
}
