package com.erg.ergspringboot.learningstandards.internal.integration;

import com.erg.ergspringboot.learningstandards.XapiStatementAccepted;
import com.erg.ergspringboot.learningstandards.XapiStatementForwarder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import com.erg.ergspringboot.platform.security.TenantContext;
import org.springframework.transaction.annotation.Propagation;

/** Forwards only the bounded, post-commit event; never performs provider I/O in a command transaction. */
@Component
public class XapiStatementAcceptedConsumer {
    private final XapiStatementForwarder forwarder;

    public XapiStatementAcceptedConsumer(XapiStatementForwarder forwarder) {
        this.forwarder = forwarder;
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(XapiStatementAccepted event) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(event.tenantId(), null, "system"))) {
            forwarder.forward(event);
        }
    }
}
