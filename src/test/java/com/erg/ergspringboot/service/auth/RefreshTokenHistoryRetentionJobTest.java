package com.erg.ergspringboot.service.auth;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.repository.auth.RefreshTokenHistoryRepository;
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
}
