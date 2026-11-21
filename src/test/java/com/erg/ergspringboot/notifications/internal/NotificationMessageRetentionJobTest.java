package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class NotificationMessageRetentionJobTest {

    @Test
    void deletesBatchesOlderThanTheWindowUntilAPartialBatchInMaintenanceScope() {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        AtomicBoolean maintenance = new AtomicBoolean();
        when(repository.deleteOlderThan(argThat(java.util.Objects::nonNull), eq(500))).thenAnswer(call -> {
            maintenance.set(TenantContext.current() != null && TenantContext.current().isMaintenance());
            return 500;
        }).thenReturn(500, 12);

        new NotificationMessageRetentionJob(repository, true, 365, 500, (com.erg.ergspringboot.platform.events.ClusterJobLock) null).purgeOldMessages();

        verify(repository, times(3)).deleteOlderThan(argThat(cutoff ->
                cutoff.isBefore(OffsetDateTime.now().minusDays(364)) && cutoff.isAfter(OffsetDateTime.now().minusDays(366))), eq(500));
        assertThat(maintenance).isTrue();
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void disabledJobDeletesNothing() {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);

        new NotificationMessageRetentionJob(repository, false, 365, 500, (com.erg.ergspringboot.platform.events.ClusterJobLock) null).purgeOldMessages();

        verify(repository, never()).deleteOlderThan(argThat(java.util.Objects::nonNull), anyInt());
    }
}
