package com.erg.ergspringboot.learningstandards.internal.integration;

import com.erg.ergspringboot.learningstandards.XapiStatementAccepted;
import com.erg.ergspringboot.learningstandards.XapiStatementForwarder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Forwards only the bounded, post-commit event; never performs provider I/O in a command transaction. */
@Component
public class XapiStatementAcceptedConsumer {
    private final XapiStatementForwarder forwarder;

    public XapiStatementAcceptedConsumer(XapiStatementForwarder forwarder) {
        this.forwarder = forwarder;
    }

    @ApplicationModuleListener
    public void on(XapiStatementAccepted event) {
        forwarder.forward(event);
    }
}
