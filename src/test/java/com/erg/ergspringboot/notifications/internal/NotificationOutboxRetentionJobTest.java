package com.erg.ergspringboot.notifications.internal;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
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
    void purgeRunsInMaintenanceTenantScopeSoRlsDoesNotHideRows() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        java.util.concurrent.atomic.AtomicBoolean maintenance = new java.util.concurrent.atomic.AtomicBoolean();
        when(repository.deleteTerminalOlderThan(argThat(java.util.Objects::nonNull), eq(1000))).thenAnswer(invocation -> {
            com.erg.ergspringboot.platform.security.TenantContext current =
                    com.erg.ergspringboot.platform.security.TenantContext.current();
            maintenance.set(current != null && current.isMaintenance());
            return 0;
        });

        new NotificationOutboxRetentionJob(repository, true, 90, 1000).purgeExpiredTerminal();

        org.assertj.core.api.Assertions.assertThat(maintenance).isTrue();
        org.assertj.core.api.Assertions.assertThat(com.erg.ergspringboot.platform.security.TenantContext.current()).isNull();
    }

    @Test
    void skipsPurgeWhenDisabled() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);

        new NotificationOutboxRetentionJob(repository, false, 90, 1000).purgeExpiredTerminal();

        verify(repository, never()).deleteTerminalOlderThan(argThat(java.util.Objects::nonNull), anyInt());
    }
}
