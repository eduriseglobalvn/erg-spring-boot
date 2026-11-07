package com.erg.ergspringboot.timekeeping.internal.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository.PhotoPurgeRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PunchPhotoRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-11-06T20:30:00Z");

    private final TkPunchRepository punches = mock(TkPunchRepository.class);
    private final PrivateObjectStorage storage = mock(PrivateObjectStorage.class);
    private final ClusterJobLock lock = mock(ClusterJobLock.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void oldPhotoIsDeletedThenKeyClearedAndCounted() {
        when(punches.findPhotoPurgeCandidates(eq(NOW.minus(Duration.ofDays(60))), eq(""), anyInt()))
                .thenReturn(List.of(ref("p1", "t1", "k1")));
        when(punches.markPhotoPurged("p1", "t1", "k1", NOW)).thenReturn(1);

        int purged = job(200).purgeOlderThan(Duration.ofDays(60));

        assertThat(purged).isEqualTo(1);
        verify(storage).delete("k1");
        verify(punches).markPhotoPurged("p1", "t1", "k1", NOW);
    }

    @Test
    void failingDeleteKeepsThatKeyAndTheRestContinue() {
        when(punches.findPhotoPurgeCandidates(any(), eq(""), anyInt()))
                .thenReturn(List.of(ref("p1", "t1", "k1"), ref("p2", "t1", "k2"), ref("p3", "t2", "k3")));
        doThrow(new IllegalStateException("r2 down")).when(storage).delete("k2");
        when(punches.markPhotoPurged(anyString(), anyString(), anyString(), any())).thenReturn(1);

        int purged = job(200).purgeOlderThan(Duration.ofDays(60));

        assertThat(purged).isEqualTo(2);
        verify(punches, never()).markPhotoPurged(eq("p2"), any(), any(), any());
        verify(storage).delete("k3");
    }

    @Test
    void fullBatchContinuesAfterTheLastIdSoBacklogIsBounded() {
        when(punches.findPhotoPurgeCandidates(any(), eq(""), eq(2)))
                .thenReturn(List.of(ref("a", "t1", "k-a"), ref("b", "t1", "k-b")));
        when(punches.findPhotoPurgeCandidates(any(), eq("b"), eq(2)))
                .thenReturn(List.of(ref("c", "t1", "k-c")));
        when(punches.markPhotoPurged(anyString(), anyString(), anyString(), any())).thenReturn(1);

        assertThat(job(2).purgeOlderThan(Duration.ofDays(60))).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void scheduledRunGoesThroughClusterJobLockWithSixtyDayDefault() {
        when(lock.callExclusively(eq(PunchPhotoRetentionJob.JOB_NAME), any(Supplier.class)))
                .thenAnswer(call -> ((Supplier<Integer>) call.getArgument(1)).get());
        when(punches.findPhotoPurgeCandidates(any(), any(), anyInt())).thenReturn(List.of());

        job(200).purge();

        verify(lock).callExclusively(eq(PunchPhotoRetentionJob.JOB_NAME), any(Supplier.class));
        verify(punches).findPhotoPurgeCandidates(eq(NOW.minus(Duration.ofDays(60))), eq(""), eq(200));
    }

    @Test
    void lockHeldElsewhereSkipsTheRun() {
        job(200).purge();

        verify(punches, never()).findPhotoPurgeCandidates(any(), any(), anyInt());
    }

    private PunchPhotoRetentionJob job(int batchSize) {
        return new PunchPhotoRetentionJob(punches, storage, clock, lock, 60, batchSize);
    }

    private static PhotoPurgeRef ref(String id, String tenantId, String key) {
        return new PhotoPurgeRef() {
            @Override public String getId() { return id; }
            @Override public String getTenantId() { return tenantId; }
            @Override public String getPhotoKey() { return key; }
        };
    }
}
