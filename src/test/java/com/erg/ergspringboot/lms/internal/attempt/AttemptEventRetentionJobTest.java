package com.erg.ergspringboot.lms.internal.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class AttemptEventRetentionJobTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    @Test
    void deletesBatchesUntilAPartialBatchWithTheConfiguredCutoff() {
        when(jdbc.update(contains("delete from attempt_events"), any(OffsetDateTime.class), anyInt())).thenReturn(100, 100, 7);
        var job = new AttemptEventRetentionJob(jdbc, manager, (com.erg.ergspringboot.platform.events.ClusterJobLock) null, 180, 100);

        assertThat(job.purge()).isEqualTo(207);

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(jdbc, times(3)).update(contains("received_at < ?"), cutoff.capture(), anyInt());
        assertThat(cutoff.getValue()).isBefore(OffsetDateTime.now().minusDays(179)).isAfter(OffsetDateTime.now().minusDays(181));
    }

    @Test
    void aRetentionBelowThirtyDaysIsRaisedSoATypoCannotPurgeRecentEvents() {
        when(jdbc.update(contains("delete from attempt_events"), any(OffsetDateTime.class), anyInt())).thenReturn(0);

        new AttemptEventRetentionJob(jdbc, manager, (com.erg.ergspringboot.platform.events.ClusterJobLock) null, 1, 100).purge();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(jdbc).update(contains("received_at < ?"), cutoff.capture(), anyInt());
        assertThat(cutoff.getValue()).isBefore(OffsetDateTime.now().minusDays(29));
    }
}
