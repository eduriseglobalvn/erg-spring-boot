package com.erg.ergspringboot.lms.internal.academicrecord;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations.AttendanceCommand;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations.GradebookScoreCommand;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class LmsAcademicRecordUseCaseTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void rejectsUnboundedAttendanceRangeBeforeSql() {
        assertThatThrownBy(() -> service().attendanceWorkspace(
                "tenant-a", "class-1", "subject-1", "2025-01-01", "2026-07-30"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnknownAttendanceStatusBeforeSql() {
        assertThatThrownBy(() -> service().upsertAttendance(new AttendanceCommand(
                "tenant-a", "teacher-1", "class-1", "student-1", "subject-1", "2026-07-30", 1, "UNKNOWN", 0)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsScoreOutsideColumnRangeBeforeSql() {
        assertThatThrownBy(() -> service().upsertGradebookScore(new GradebookScoreCommand(
                "tenant-a", "teacher-1", "class-1", "student-1", "subject-1", "quiz-1", "Quiz 1",
                BigDecimal.TEN, "QUIZ", 0, BigDecimal.valueOf(11), 0)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(jdbc);
    }

    private LmsAcademicRecordUseCase service() { return new LmsAcademicRecordUseCase(jdbc); }
}
