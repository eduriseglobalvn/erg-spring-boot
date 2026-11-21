package com.erg.ergspringboot.auth.internal.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.users.persistence.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AuthDataRetentionJobTest {
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final AuthLoginAttemptRepository attempts = mock(AuthLoginAttemptRepository.class);

    @Test
    void purgesBothTablesBeyondRetentionAndDrainsFullBatches() {
        when(sessions.deleteDeadBefore(any(), eq(1000))).thenReturn(1000, 7);
        OffsetDateTime before = OffsetDateTime.now().minusDays(91);

        new AuthDataRetentionJob(sessions, attempts, (ClusterJobLock) null, 90).purgeOldAuthData();

        verify(sessions, times(2)).deleteDeadBefore(argThat(c -> c.isAfter(before)
                && c.isBefore(OffsetDateTime.now().minusDays(89))), eq(1000));
        verify(attempts).deleteOlderThan(any(), eq(1000));
    }

    @Test
    void runsUnderTheClusterLock() {
        ClusterJobLock lock = mock(ClusterJobLock.class);

        new AuthDataRetentionJob(sessions, attempts, lock, 90).purgeOldAuthData();

        verify(lock).runExclusively(eq(AuthDataRetentionJob.JOB_NAME), any(Runnable.class));
        verifyNoInteractions(sessions, attempts);
    }
}
