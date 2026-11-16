package com.erg.ergspringboot.lms.internal.classlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.erg.ergspringboot.calendar.TeachingScheduleITBase;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonReportMatrix;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** CBG B7 on a real database. Clock: Thu 12/11/2026 08:00 Vietnam, so Mon-Wed are over and Thursday's period is not. */
class LessonReportIT extends TeachingScheduleITBase {

    private static final String MONDAY = "2026-11-09";

    @Autowired LessonReportService reports;
    @Autowired LessonLogCommandUseCase commands;

    @Test
    void theMatrixSeparatesOnTimeLateAndMissingAndTheReminderOnlyReachesTeachersWhoOwe() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        String classId = classroom(tenant, school, "6A6");
        String subject = subject(tenant, "Tin hoc");
        String anh = user(tenant, "Anh Nguyen");
        String binh = user(tenant, "Binh Tran");
        String batch = batch(tenant, school, "PUBLISHED", "HK1");
        event(tenant, batch, school, classId, subject, "PUBLISHED", at(MONDAY, "08:00"), 1, anh, null, null);
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-10", "08:00"), 1, binh, null, null);
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-11", "08:00"), 1, anh, null, null);
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-12", "08:00"), 1, binh, null, null);
        // another tenant's teacher never shows up
        String otherTenant = id();
        String otherSchool = school(otherTenant, "Other");
        event(otherTenant, batch(otherTenant, otherSchool, "PUBLISHED", "X"), otherSchool, classroom(otherTenant, otherSchool, "1A"),
                subject(otherTenant, "Toan"), "PUBLISHED", at(MONDAY, "08:00"), 1, user(otherTenant, "Zed"), null, null);

        commands.confirmWeek(tenant, anh, MONDAY); // Mon and Wed, both after their day: late
        write("update lms_lesson_log_entries set late = false where tenant_id = ? and lesson_date = date '2026-11-11'", tenant);

        LessonReportMatrix matrix = reports.matrix(tenant, MONDAY, null, null, null);

        assertThat(matrix.days()).hasSize(5);
        assertThat(matrix.rows()).extracting(r -> r.teacherName()).containsExactly("Anh Nguyen", "Binh Tran");
        assertThat(matrix.rows().get(0).schoolName()).isEqualTo("THCS Nguyen Du");
        assertThat(matrix.rows().get(0).cells()).extracting(c -> c.state())
                .containsExactly("LATE", "NONE", "ON_TIME", "NONE", "NONE");
        assertThat(matrix.rows().get(1).cells()).extracting(c -> c.state())
                .containsExactly("NONE", "MISSING", "NONE", "NONE", "NONE");
        assertThat(matrix.stats()).satisfies(s -> {
            assertThat(s.periods()).isEqualTo(3);
            assertThat(s.teachers()).isEqualTo(2);
            assertThat(s.schools()).isEqualTo(1);
            assertThat(s.onTime()).isEqualTo(1);
            assertThat(s.late()).isEqualTo(1);
            assertThat(s.missing()).isEqualTo(1);
        });
        assertThat(reports.matrix(tenant, MONDAY, null, null, List.of("another-school")).rows()).isEmpty();
        assertThat(reports.matrix(tenant, MONDAY, school, null, List.of(school)).rows()).hasSize(2);

        assertThat(reports.unconfirmedByTeacher(tenant, LocalDate.parse(MONDAY), LocalDate.parse("2026-11-14"), null))
                .isEqualTo(Map.of(binh, 1));
        // Anh owes nothing, an unknown id and a teacher of another tenant are ignored
        assertThat(reports.remind(tenant, "admin", List.of(anh, binh, "ghost"), null)).isEqualTo(1);
        verify(notifications, times(1)).enqueue(argThat(c -> c.userIds().equals(List.of(binh))
                && c.tenantId().equals(tenant) && c.idempotencyKey().startsWith("lesson-report-manual:" + binh)));
    }
}
