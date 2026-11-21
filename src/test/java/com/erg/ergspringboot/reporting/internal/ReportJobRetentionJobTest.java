package com.erg.ergspringboot.reporting.internal;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.reporting.persistence.ReportJobRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ReportJobRetentionJobTest {

    @Test
    void deletesJobsThirtyDaysAfterExpiryInBatchesUntilAPartialBatch() {
        ReportJobRepository repository = mock(ReportJobRepository.class);
        when(repository.deleteExpiredBefore(argThat(java.util.Objects::nonNull), eq(500))).thenReturn(500, 3);

        new ReportJobRetentionJob(repository, true, 30, 500, (com.erg.ergspringboot.platform.events.ClusterJobLock) null).purgeExpiredJobs();

        verify(repository, times(2)).deleteExpiredBefore(argThat(cutoff ->
                cutoff.isBefore(OffsetDateTime.now().minusDays(29)) && cutoff.isAfter(OffsetDateTime.now().minusDays(31))), eq(500));
    }

    @Test
    void disabledJobDeletesNothing() {
        ReportJobRepository repository = mock(ReportJobRepository.class);

        new ReportJobRetentionJob(repository, false, 30, 500, (com.erg.ergspringboot.platform.events.ClusterJobLock) null).purgeExpiredJobs();

        verify(repository, never()).deleteExpiredBefore(argThat(java.util.Objects::nonNull), anyInt());
    }
}
