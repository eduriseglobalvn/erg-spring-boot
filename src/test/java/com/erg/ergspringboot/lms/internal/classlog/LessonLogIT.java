package com.erg.ergspringboot.lms.internal.classlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleITBase;
import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogCommandUseCase.AttendanceMark;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogPeriod;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogWeek;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CBG B3-B5 on a real database: the board's class 6A6 (PPCT 10/11, taught 8/9), absentees from per-period attendance,
 * confirming the week, edits after confirmation and the closed-month freeze. Clock: Thu 12/11/2026 08:00 Vietnam.
 */
class LessonLogIT extends TeachingScheduleITBase {

    private static final String MONDAY = "2026-11-09";

    @Autowired LessonLogQueryUseCase queries;
    @Autowired LessonLogCommandUseCase commands;

    private String tenant;
    private String teacher;
    private String classId;
    private String schoolId;
    private String curriculumSubject;
    private String mondayFirst;
    private String mondaySecond;
    private String friday;
    private String minh;
    private String khoa;

    private void setUp() {
        tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        classId = classroom(tenant, school, "6A6");
        schoolId = school;
        String subject = subject(tenant, "Tin hoc");
        curriculumSubject = subject;
        teacher = user(tenant, "Nguyen Quoc Vuong");
        String level = id();
        write("insert into curriculum_subjects (id, tenant_id, code, name, created_at, updated_at)"
                + " values (?, ?, 'tin', 'Tin hoc', now(), now())", subject, tenant);
        write("insert into curriculum_levels (id, tenant_id, subject_id, code, name, created_at, updated_at)"
                + " values (?, ?, ?, 'l1', 'Level 1', now(), now())", level, tenant, subject);
        // lms_levels carries the level of timetable events; its ids are preserved in curriculum_levels
        write("insert into lms_levels (id, tenant_id, subject_id, code, name, created_at, updated_at)"
                + " values (?, ?, ?, 'l1', 'Level 1', now(), now())", level, tenant, subject);
        String program = id();
        write("insert into curriculum_programs (id, tenant_id, subject_id, level_id, academic_year, name, status)"
                + " values (?, ?, ?, ?, '2026-2027', 'KCT', 'published')", program, tenant, subject, level);
        for (int period = 1; period <= 20; period++) {
            String title = period == 8 ? "Bai 5" : period == 9 ? "Bai 6" : "KCT " + period;
            write("insert into curriculum_program_rows (id, tenant_id, program_id, topic, period, title, sort_order)"
                    + " values (?, ?, ?, 'Chu de', ?, ?, ?)", id(), tenant, program, period, title, period);
        }
        write("insert into curriculum_teaching_plan_settings (id, tenant_id, school_id, subject_id, academic_year, start_date)"
                + " values (?, ?, ?, ?, '2026-2027', date '2026-11-02')", id(), tenant, school, subject);

        String batch = batch(tenant, school, "PUBLISHED", "HK1");
        // nine periods in the first plan week, so Monday's two periods are PPCT 10 and 11
        String[] firstWeek = {"02", "02", "03", "03", "04", "04", "05", "05", "06"};
        for (int i = 0; i < firstWeek.length; i++) {
            event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-" + firstWeek[i], i % 2 == 0 ? "08:00" : "09:00"),
                    1 + i % 2, teacher, null, null);
        }
        mondayFirst = event(tenant, batch, school, classId, subject, "PUBLISHED", at(MONDAY, "08:00"), 1, teacher, null, null);
        mondaySecond = event(tenant, batch, school, classId, subject, "PUBLISHED", at(MONDAY, "09:00"), 2, teacher, null, null);
        friday = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-13", "08:00"), 1, teacher, null, null);
        write("update teaching_schedule_events set level_id = ? where tenant_id = ?", level, tenant);

        minh = student("Nguyen Van Minh", school);
        khoa = student("Tran Khoa", school);
    }

    private String student(String name, String school) {
        String sid = id().substring(0, 24);
        write("insert into lms_students (id, created_at, updated_at, tenant_id, school_id, academic_class_id, full_name)"
                + " values (?, now(), now(), ?, ?, ?, ?)", sid, tenant, school, classId, name);
        return sid;
    }

    private String codeOf(Runnable action) {
        return ((ApiException) org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, action::run)).getCode();
    }

    @Test
    void theWeekShowsPpctLagAbsenteesLateConfirmationAndTheClosedMonthFreeze() {
        setUp();

        LessonLogWeek fresh = queries.teacherWeek(tenant, teacher, MONDAY, null, null);
        LessonLogPeriod first = fresh.days().get(0).periods().get(0);
        assertThat(fresh.days()).hasSize(5);
        assertThat(first.curriculumPeriodCount()).isEqualTo(20);
        assertThat(first.ppctPeriod()).isEqualTo(10);
        assertThat(first.actualPeriod()).isEqualTo(10);
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.happened()).isTrue();
        assertThat(fresh.days().get(4).periods().get(0).happened()).isFalse();
        assertThat(fresh.stats().taught()).isEqualTo(2);
        assertThat(fresh.lagChip()).isNull();

        // B5: attendance of period 1 and the log of that period in one call
        LessonLogPeriod saved = commands.saveAttendanceAndLog(tenant, teacher, mondayFirst, MONDAY, 1,
                List.of(new AttendanceMark(minh, "ABSENT"), new AttendanceMark(khoa, "EXCUSED")), 8, "Hoc sinh tap trung", 0);
        assertThat(saved.actualPeriod()).isEqualTo(8);
        assertThat(saved.lessonTitle()).isEqualTo("Bai 5");
        assertThat(saved.version()).isZero();
        assertThat(saved.absentees()).extracting(a -> a.shortName() + (a.excused() ? "(P)" : "(K)"))
                .containsExactlyInAnyOrder("Minh(K)", "Khoa(P)");

        commands.update(tenant, teacher, mondaySecond, MONDAY, 2, 9, null, 0);
        LessonLogWeek lagging = queries.teacherWeek(tenant, teacher, MONDAY, null, null);
        assertThat(lagging.lagChip().lag()).isEqualTo(2);
        assertThat(lagging.lagChip().className()).isEqualTo("6A6");
        assertThat(lagging.days().get(0).periods()).extracting(LessonLogPeriod::lessonTitle).containsExactly("Bai 5", "Bai 6");
        assertThat(queries.classWeek(tenant, classId, MONDAY).days().get(0).periods()).hasSize(2);
        assertThat(queries.classWeek(tenant, classId, MONDAY).days().get(0).periods().get(0).subjectId()).isNotBlank();
        assertThat(queries.teacherDayPeriods(tenant, teacher, MONDAY)).hasSize(2);

        // B4: another teacher cannot write, a stale version is refused
        assertThat(codeOf(() -> commands.update(tenant, user(tenant, "Other"), mondayFirst, MONDAY, 1, 8, null, null)))
                .isEqualTo("NOT_TEACHER_OF_PERIOD");
        assertThat(codeOf(() -> commands.update(tenant, teacher, mondayFirst, MONDAY, 1, 7, null, 5))).isEqualTo("STALE_VERSION");

        // confirming the week: Monday's periods were taught on Thursday, so they are late; Friday has not happened
        var stats = commands.confirmWeek(tenant, teacher, MONDAY);
        assertThat(stats.confirmed()).isEqualTo(2);
        assertThat(stats.pending()).isZero();
        LessonLogPeriod confirmed = queries.teacherWeek(tenant, teacher, MONDAY, null, "CONFIRMED").days().get(0).periods().get(0);
        assertThat(confirmed.late()).isTrue();

        // editing after confirmation keeps it confirmed and writes the log
        LessonLogPeriod edited = commands.update(tenant, teacher, mondayFirst, MONDAY, 1, 9, "Sua", confirmed.version());
        assertThat(edited.status()).isEqualTo("CONFIRMED");
        var changes = queries.changes(tenant, teacher, MONDAY, null);
        assertThat(changes).extracting(c -> c.field() + ":" + c.oldValue() + ">" + c.newValue())
                .contains("actualPeriod:8>9", "comment:Hoc sinh tap trung>Sua");
        assertThat(changes.get(0).actorName()).isEqualTo("Nguyen Quoc Vuong");

        // a closed month freezes the log
        write("insert into tk_teaching_months (id, tenant_id, month, status, version) values (?, ?, '2026-11', 'CLOSED', 0)", id(), tenant);
        assertThat(codeOf(() -> commands.update(tenant, teacher, mondayFirst, MONDAY, 1, 8, null, null))).isEqualTo("MONTH_CLOSED");
        assertThat(friday).isNotNull();
    }

    @Test
    void aTimetableSubjectWithoutAProgramReadsTheKctOfTheSubjectAllocatedToTheClass() {
        setUp();
        String other = subject(tenant, "Tieng Anh");
        write("update teaching_schedule_events set subject_id = ?, level_id = null where tenant_id = ?", other, tenant);

        assertThat(queries.teacherWeek(tenant, teacher, MONDAY, null, null).days().get(0).periods().get(0).lessonTitle()).isNull();

        write("insert into curriculum_class_subject_allocations (id, tenant_id, school_id, academic_year, academic_class_id, subject_id, version, created_at, updated_at)"
                + " values (?, ?, ?, '2026-2027', ?, ?, 1, now(), now())", id(), tenant, schoolId, classId, curriculumSubject);
        LessonLogPeriod first = queries.teacherWeek(tenant, teacher, MONDAY, null, null).days().get(0).periods().get(0);
        assertThat(first.curriculumPeriodCount()).as("kct size").isEqualTo(20);
        assertThat(first.actualPeriod()).as("actual").isEqualTo(10);
        assertThat(first.lessonTitle()).isEqualTo("KCT 10");
    }

    @Test
    void aWeekWithOnlyPeriodsStillToComeCannotBeConfirmedAndAnEmptyWeekIsANoOp() {
        setUp();
        String school = jdbc.queryForObject("select school_id from teaching_schedule_events where id = ?", String.class, friday);
        String subject = jdbc.queryForObject("select subject_id from teaching_schedule_events where id = ?", String.class, friday);
        String batch = jdbc.queryForObject("select batch_id from teaching_schedule_events where id = ?", String.class, friday);
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-16", "08:00"), 1, teacher, null, null);

        assertThat(codeOf(() -> commands.confirmWeek(tenant, teacher, "2026-11-16"))).isEqualTo("PERIOD_NOT_HAPPENED");
        assertThat(commands.confirmWeek(tenant, teacher, "2026-11-23").confirmed()).isZero();
        assertThat(codeOf(() -> commands.confirmWeek(tenant, teacher, "2026-11-11"))).isEqualTo("BAD_REQUEST");
    }

    @Test
    void anotherTeacherOrAHeadReadsTheWholeClassLogButOnlyTheOwnerWritesItsPeriods() {
        setUp();
        commands.update(tenant, teacher, mondayFirst, MONDAY, 1, 8, "Ghi chu cua co giao", 0);
        String head = user(tenant, "To truong chuyen mon");

        LessonLogPeriod seenByHead = queries.classWeek(tenant, classId, MONDAY).days().get(0).periods().get(0);
        assertThat(seenByHead.comment()).isEqualTo("Ghi chu cua co giao");
        assertThat(seenByHead.teacherName()).isEqualTo("Nguyen Quoc Vuong");
        assertThat(codeOf(() -> commands.update(tenant, head, mondayFirst, MONDAY, 1, 9, "Sua ho", null)))
                .isEqualTo("NOT_TEACHER_OF_PERIOD");
        assertThat(queries.classWeek(tenant, classId, MONDAY).days().get(0).periods().get(0).comment())
                .isEqualTo("Ghi chu cua co giao");
    }
}
