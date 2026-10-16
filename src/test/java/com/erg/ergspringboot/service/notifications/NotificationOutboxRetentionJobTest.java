package com.erg.ergspringboot.service.notifications;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class NotificationOutboxRetentionJobTest {

    @Test
    void purgesTerminalRowsOlderThanRetentionWindow() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        OffsetDateTime ninetyOneDaysAgo = OffsetDateTime.now().minusDays(91);
        when(repository.deleteTerminalOlderThan(argThat(java.util.Objects::nonNull), eq(1000))).thenReturn(0);

        new NotificationOutboxRetentionJob(repository, true, 90, 1000).purgeExpiredTerminal();

        verify(repository).deleteTerminalOlderThan(argThat(cutoff ->
                cutoff.isAfter(ninetyOneDaysAgo) && cutoff.isBefore(OffsetDateTime.now().minusDays(89))), eq(1000));
    }

    @Test
    void loopsUntilPartialBatchThenStops() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        // Full batch twice, then a partial batch signals the tail is reached.
        when(repository.deleteTerminalOlderThan(argThat(java.util.Objects::nonNull), eq(500)))
                .thenReturn(500, 500, 42);

        new NotificationOutboxRetentionJob(repository, true, 90, 500).purgeExpiredTerminal();

        verify(repository, times(3)).deleteTerminalOlderThan(argThat(java.util.Objects::nonNull), eq(500));
    }

    @Test
    void skipsPurgeWhenDisabled() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);

        new NotificationOutboxRetentionJob(repository, false, 90, 1000).purgeExpiredTerminal();

        verify(repository, never()).deleteTerminalOlderThan(argThat(java.util.Objects::nonNull), anyInt());
    }
}
