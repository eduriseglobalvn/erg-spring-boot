package com.erg.ergspringboot.timekeeping.internal.day;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DailyCloseJobTest {

    // 00:30 on Fri 13/11/2026 in Asia/Ho_Chi_Minh is still the 12th in UTC.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T17:30:00Z"), ZoneOffset.UTC);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 11, 12);

    private final TkShiftRepository shifts = mock(TkShiftRepository.class);
    private final DayAggregationService days = mock(DayAggregationService.class);

    @Test
    void closesYesterdayInTheVietnamZoneForEveryTenantAndOneFailingTenantDoesNotStopTheRest() {
        when(shifts.findTenantIds()).thenReturn(List.of("t1", "t2", "t3"));
        when(days.closeDay("t1", YESTERDAY)).thenReturn(3);
        when(days.closeDay("t2", YESTERDAY)).thenThrow(new IllegalStateException("db"));
        when(days.closeDay("t3", YESTERDAY)).thenReturn(2);

        int rows = new DailyCloseJob(shifts, days, runningLock(), CLOCK).run();

        assertThat(rows).isEqualTo(5);
    }

    @Test
    void lockHeldElsewhereSkipsTheRun() {
        ClusterJobLock heldElsewhere = new ClusterJobLock(null) {
            @Override
            public <T> T callExclusively(String jobName, Supplier<T> task) {
                return null;
            }
        };

        assertThat(new DailyCloseJob(shifts, days, heldElsewhere, CLOCK).run()).isZero();

        verify(shifts, never()).findTenantIds();
        verify(days, never()).closeDay(any(), any());
    }

    private static ClusterJobLock runningLock() {
        return new ClusterJobLock(null) {
            @Override
            public <T> T callExclusively(String jobName, Supplier<T> task) {
                assertThat(jobName).isEqualTo(DailyCloseJob.JOB_NAME);
                return task.get();
            }
        };
    }
}
