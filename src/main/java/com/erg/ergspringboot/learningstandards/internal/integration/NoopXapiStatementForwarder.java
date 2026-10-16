package com.erg.ergspringboot.learningstandards.internal.integration;

import com.erg.ergspringboot.learningstandards.XapiStatementAccepted;
import com.erg.ergspringboot.learningstandards.XapiStatementForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Safe default until an LRS adapter is configured. */
@Component
public final class NoopXapiStatementForwarder implements XapiStatementForwarder {
    private static final Logger log = LoggerFactory.getLogger(NoopXapiStatementForwarder.class);

    @Override
    public void forward(XapiStatementAccepted event) {
        log.debug("xAPI forwarding adapter is not configured eventId={} tenantId={} statementId={}",
                event.eventId(), event.tenantId(), event.statementId());
    }
}
