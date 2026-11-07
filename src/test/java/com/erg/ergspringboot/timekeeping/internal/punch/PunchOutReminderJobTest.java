package com.erg.ergspringboot.timekeeping.internal.punch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PunchOutReminderJobTest {

    /** Thursday 12/11/2026 11:20 in Ho Chi Minh, ten minutes before an 11:30 shift end. */
    private static final Instant AT_1120 = Instant.parse("2026-11-12T04:20:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private final TkShiftRepository shifts = mock(TkShiftRepository.class);
    private final TkPunchRepository punches = mock(TkPunchRepository.class);
    private final NotificationAdminOperations notifications = mock(NotificationAdminOperations.class);
    private final ClusterJobLock lock = new ClusterJobLock(null) {
        @Override
        public <T> T callExclusively(String jobName, Supplier<T> task) {
            return task.get();
        }
    };

    @Test
    void shiftEndingInTenMinutesRemindsEachPersonWithAnInButNoOut() {
        when(shifts.findEndingBetween(4, LocalTime.of(11, 20), LocalTime.of(11, 30))).thenReturn(List.of(shift()));
        when(punches.findUsersInWithoutOut("tenant-a", "shift-1", DAY)).thenReturn(List.of("teacher-1"));

        int sent = job(AT_1120).remindDue();

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<AdminSendRequest> request = ArgumentCaptor.forClass(AdminSendRequest.class);
        verify(notifications).send(eq("tenant-a"), eq("system"), request.capture());
        assertThat(request.getValue().title()).isEqualTo("Nhắc chấm ra");
        assertThat(request.getValue().idempotencyKey()).isEqualTo("tk-out:teacher-1:2026-11-12");
        assertThat(request.getValue().userIds()).containsExactly("teacher-1");
        assertThat(request.getValue().bodyPreview()).contains("11:30");
    }

    @Test
    void personWhoAlreadyPunchedOutIsNotReminded() {
        when(shifts.findEndingBetween(4, LocalTime.of(11, 20), LocalTime.of(11, 30))).thenReturn(List.of(shift()));
        when(punches.findUsersInWithoutOut("tenant-a", "shift-1", DAY)).thenReturn(List.of());

        assertThat(job(AT_1120).remindDue()).isZero();
        verify(notifications, never()).send(anyString(), anyString(), any());
    }

    @Test
    void runningTwiceUsesTheSameOutboxKeySoOnlyOneNotificationExists() {
        when(shifts.findEndingBetween(4, LocalTime.of(11, 20), LocalTime.of(11, 30))).thenReturn(List.of(shift()));
        when(shifts.findEndingBetween(4, LocalTime.of(11, 25), LocalTime.of(11, 35))).thenReturn(List.of(shift()));
        when(punches.findUsersInWithoutOut("tenant-a", "shift-1", DAY)).thenReturn(List.of("teacher-1"));

        job(AT_1120).remindDue();
        job(Instant.parse("2026-11-12T04:25:00Z")).remindDue();

        ArgumentCaptor<AdminSendRequest> request = ArgumentCaptor.forClass(AdminSendRequest.class);
        verify(notifications, times(2)).send(eq("tenant-a"), eq("system"), request.capture());
        Set<String> keys = new HashSet<>();
        request.getAllValues().forEach(r -> keys.add(r.idempotencyKey()));
        // NotificationCommandService returns the existing message for a repeated dedupe key.
        assertThat(keys).containsExactly("tk-out:teacher-1:2026-11-12");
    }

    @Test
    void oneFailedSendDoesNotStopTheOthers() {
        when(shifts.findEndingBetween(4, LocalTime.of(11, 20), LocalTime.of(11, 30))).thenReturn(List.of(shift()));
        when(punches.findUsersInWithoutOut("tenant-a", "shift-1", DAY)).thenReturn(List.of("u-1", "u-2"));
        when(notifications.send(eq("tenant-a"), eq("system"), any()))
                .thenThrow(new IllegalStateException("boom")).thenReturn(null);

        assertThat(job(AT_1120).remindDue()).isEqualTo(1);
    }

    private PunchOutReminderJob job(Instant now) {
        return new PunchOutReminderJob(shifts, punches, notifications, lock, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static TkShift shift() {
        TkShift s = new TkShift();
        s.setId("shift-1");
        s.setTenantId("tenant-a");
        s.setSchoolId("school-1");
        s.setName("Ca sáng");
        s.setStartTime(LocalTime.of(7, 30));
        s.setEndTime(LocalTime.of(11, 30));
        s.setWeekdays(new Integer[] {1, 2, 3, 4, 5});
        return s;
    }
}
