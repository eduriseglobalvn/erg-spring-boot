package com.erg.ergspringboot.auth.internal.refresh;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.users.persistence.auth.RefreshTokenHistoryRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RefreshTokenHistoryRetentionJobTest {
    @Test
    void removesHistoryOlderThanThirtyDays() {
        RefreshTokenHistoryRepository repository = mock(RefreshTokenHistoryRepository.class);
        OffsetDateTime before = OffsetDateTime.now().minusDays(31);

        new RefreshTokenHistoryRetentionJob(repository).deleteExpiredHistory();

        verify(repository).deleteByConsumedAtBefore(argThat(cutoff ->
                cutoff.isAfter(before) && cutoff.isBefore(OffsetDateTime.now().minusDays(29))));
    }

    @Test
    void purgeRunsUnderTheClusterLockSoOnlyOneInstanceDeletes() {
        RefreshTokenHistoryRepository repository = mock(RefreshTokenHistoryRepository.class);
        ClusterJobLock lock = mock(ClusterJobLock.class);

        new RefreshTokenHistoryRetentionJob(repository, lock).deleteExpiredHistory();

        // The mocked lock never runs the task: another instance holds it.
        verify(lock).runExclusively(eq(RefreshTokenHistoryRetentionJob.JOB_NAME), any(Runnable.class));
        verifyNoInteractions(repository);
    }
}
