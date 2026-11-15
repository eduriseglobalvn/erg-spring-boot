package com.erg.ergspringboot.lms.internal.classlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.lms.LessonLogMonthLock;
import com.erg.ergspringboot.lms.LessonScheduleReadPort;
import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogPeriod;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogReader.Row;
import com.erg.ergspringboot.lms.persistence.LessonLogChange;
import com.erg.ergspringboot.lms.persistence.LessonLogChangeRepository;
import com.erg.ergspringboot.lms.persistence.LessonLogEntry;
import com.erg.ergspringboot.lms.persistence.LessonLogEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LessonLogCommandUseCaseTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
    private static final Instant NOW = Instant.parse("2026-11-12T01:00:00Z");

    private final LessonScheduleReadPort schedule = mock(LessonScheduleReadPort.class);
    private final LessonLogReader reader = mock(LessonLogReader.class);
    private final LessonLogEntryRepository entries = mock(LessonLogEntryRepository.class);
    private final LessonLogChangeRepository changes = mock(LessonLogChangeRepository.class);
    private final LessonLogMonthLock lock = mock(LessonLogMonthLock.class);
    private final LmsAcademicRecordOperations attendance = mock(LmsAcademicRecordOperations.class);
    private final LessonLogCommandUseCase useCase = new LessonLogCommandUseCase(
            schedule, reader, entries, changes, lock, attendance, Clock.fixed(NOW, ZoneOffset.UTC));
    private final LessonSlot slot = new LessonSlot("e1", "6A6", "tin", DAY, 1, 1,
            Instant.parse("2026-11-09T00:00:00Z"), Instant.parse("2026-11-09T00:45:00Z"), "school", "lvl", "MORNING", List.of("t1"));

    @BeforeEach
    void ownPeriod() {
        when(schedule.teacherSlots("tn", "t1", DAY, DAY)).thenReturn(List.of(slot));
        when(entries.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private Row row(LessonLogEntry entry, int ppct, int actual, int version, String comment) {
        return new Row(slot, entry, new LessonLogPeriod("e1", DAY, 1, "6A6", "6A6", "s1", "Tin", null, ppct, actual, null, List.of(),
                comment, entry == null ? "PENDING" : entry.getStatus(), false, true, version));
    }

    private void given(Row row) {
        when(reader.rows(any(), anyList(), any())).thenReturn(List.of(row));
    }

    private static String codeOf(Runnable action) {
        return ((ApiException) org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, action::run)).getCode();
    }

    @Test
    void aTeacherWhoDoesNotTeachThePeriodIsRefused() {
        when(reader.rows(any(), anyList(), any())).thenReturn(List.of());

        assertThat(codeOf(() -> useCase.update("tn", "t1", "e1", "2026-11-09", 1, 3, null, null)))
                .isEqualTo("NOT_TEACHER_OF_PERIOD");
    }

    @Test
    void aStaleExpectedVersionIsRefused() {
        given(row(null, 10, 10, 3, null));

        assertThat(codeOf(() -> useCase.update("tn", "t1", "e1", "2026-11-09", 1, 8, null, 2))).isEqualTo("STALE_VERSION");
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void aClosedMonthFreezesTheLog() {
        given(row(null, 10, 10, 0, null));
        when(lock.isClosed("tn", YearMonth.of(2026, 11))).thenReturn(true);

        assertThat(codeOf(() -> useCase.update("tn", "t1", "e1", "2026-11-09", 1, 8, null, null))).isEqualTo("MONTH_CLOSED");
    }

    @Test
    void theActualPeriodMustFitTheFramework() {
        given(row(null, 10, 10, 0, null));
        when(reader.frameworkSize("tn", slot)).thenReturn(20);

        assertThat(codeOf(() -> useCase.update("tn", "t1", "e1", "2026-11-09", 1, 21, null, null)))
                .isEqualTo("ACTUAL_PERIOD_OUT_OF_RANGE");
        assertThat(codeOf(() -> useCase.update("tn", "t1", "e1", "2026-11-09", 1, 0, null, null)))
                .isEqualTo("ACTUAL_PERIOD_OUT_OF_RANGE");
    }

    @Test
    void editingAfterConfirmationKeepsItConfirmedAndWritesTheLog() {
        LessonLogEntry confirmed = new LessonLogEntry();
        confirmed.setId("entry-1");
        confirmed.setStatus("CONFIRMED");
        confirmed.setActualPeriod(8);
        given(row(confirmed, 10, 8, 4, "cu"));

        useCase.update("tn", "t1", "e1", "2026-11-09", 1, 9, "moi", 4);

        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getActualPeriod()).isEqualTo(9);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LessonLogChange>> saved = ArgumentCaptor.forClass(List.class);
        verify(changes).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(LessonLogChange::getField, LessonLogChange::getOldValue, LessonLogChange::getNewValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("actualPeriod", "8", "9"),
                        org.assertj.core.groups.Tuple.tuple("comment", "cu", "moi"));
    }

    @Test
    void anUnchangedEditDoesNotBumpTheVersion() {
        LessonLogEntry entry = new LessonLogEntry();
        entry.setStatus("PENDING");
        given(row(entry, 10, 8, 1, "x"));

        useCase.update("tn", "t1", "e1", "2026-11-09", 1, 8, " x ", 1);

        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void attendanceIsNotSavedWhenTheLogPartIsInvalid() {
        given(row(null, 10, 10, 0, null));
        when(reader.frameworkSize("tn", slot)).thenReturn(5);

        assertThat(codeOf(() -> useCase.saveAttendanceAndLog("tn", "t1", "e1", "2026-11-09", 1,
                List.of(new LessonLogCommandUseCase.AttendanceMark("s1", "ABSENT")), 9, null, null)))
                .isEqualTo("ACTUAL_PERIOD_OUT_OF_RANGE");
        verify(attendance, never()).upsertAttendance(any());
    }

    @Test
    void confirmationIsLateOnlyAfterMidnightOfTheTeachingDayInVietnam() {
        // 23:59 on 09/11 in Vietnam is 16:59Z
        assertThat(LessonLogCommandUseCase.isLate(DAY, Instant.parse("2026-11-09T16:59:00Z"))).isFalse();
        assertThat(LessonLogCommandUseCase.isLate(DAY, Instant.parse("2026-11-09T17:00:00Z"))).isTrue();
    }
}
