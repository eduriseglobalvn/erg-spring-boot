package com.erg.ergspringboot.learningstandards;

import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.learningstandards.internal.integration.XapiStatementAcceptedConsumer;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class XapiStatementAcceptedConsumerTest {
    @Test
    void forwardsOnlyTheBoundedPostCommitEvent() {
        XapiStatementForwarder forwarder = Mockito.mock(XapiStatementForwarder.class);
        XapiStatementAccepted event = new XapiStatementAccepted(
                "event-1", "tenant-1", "statement-1", "actor-hash", 0L,
                OffsetDateTime.now(), 1, "corr-1", "corr-1");

        new XapiStatementAcceptedConsumer(forwarder).on(event);

        verify(forwarder).forward(event);
    }
}
