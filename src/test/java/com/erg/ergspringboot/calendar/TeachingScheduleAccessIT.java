package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleDraftInput.Row;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.AccessChangeResponse;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.projection.ClassOptionProjection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** B5: schedule-derived access (login school scope, class lists) and the accessChanges preview of a draft. */
class TeachingScheduleAccessIT extends TeachingScheduleITBase {

    @Autowired ClassroomRepository classrooms;
    @Autowired TeachingScheduleOperations schedule;

    /** The repository natives use the real clock, so these events are placed relative to the real "now". */
    private static OffsetDateTime sinceNow() {
        return LocalDate.now(VN).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(14)
                .atStartOfDay(VN).toOffsetDateTime();
    }

    private List<String> classIds(String tenant, String teacher) {
        return transactions.execute(s -> classrooms.findTeacherClassOptions(tenant, teacher).stream()
                .map(ClassOptionProjection::id).toList());
    }

    private List<String> schoolIds(String tenant, String teacher) {
        return transactions.execute(s -> classrooms.findTeacherManagedSchoolIds(tenant, teacher));
    }

    @Test
    void publishedEventsKeepAccessUntilTheCutOffAndNothingElseDoes() {
        String tenant = id();
        String schoolKept = school(tenant, "THCS Giữ quyền");
        String schoolLapsed = school(tenant, "THCS Mất quyền");
        String schoolDraft = school(tenant, "THCS Chỉ nháp");
        String schoolCancelled = school(tenant, "THCS Đã huỷ");
        String kept = classroom(tenant, schoolKept, "9A2");
        String lapsed = classroom(tenant, schoolLapsed, "9A3");
        String draftOnly = classroom(tenant, schoolDraft, "9A4");
        String cancelled = classroom(tenant, schoolCancelled, "9A5");
        String future = classroom(tenant, schoolKept, "9A6");
        String subject = subject(tenant, "Toán");
        String teacher = user(tenant, "Quốc Vương");
        String assistant = user(tenant, "Bảo Ngọc");
        String stranger = user(tenant, "Người lạ");
        OffsetDateTime since = sinceNow();

        String batchKept = batch(tenant, schoolKept, "PUBLISHED", "Đợt 1");
        // event(...) lasts 45 minutes: this one ends exactly 5 minutes after the cut-off
        event(tenant, batchKept, schoolKept, kept, subject, "PUBLISHED", since.minusMinutes(40), 1, teacher, assistant, null);
        event(tenant, batch(tenant, schoolLapsed, "PUBLISHED", "Đợt 1"), schoolLapsed, lapsed, subject, "PUBLISHED",
                since.minusMinutes(50), 1, teacher, null, null);
        event(tenant, batch(tenant, schoolDraft, "DRAFT", "Đợt 1"), schoolDraft, draftOnly, subject, "DRAFT",
                since.plusDays(1), 1, teacher, null, null);
        event(tenant, batch(tenant, schoolCancelled, "PUBLISHED", "Đợt 1"), schoolCancelled, cancelled, subject, "CANCELLED",
                since.plusDays(1), 1, teacher, null, null);
        event(tenant, batchKept, schoolKept, future, subject, "PUBLISHED", OffsetDateTime.now().plusDays(30), 1, teacher, null, null);

        // assistants count like the main teacher; drafts, cancelled events and lapsed schools do not
        assertThat(classIds(tenant, teacher)).containsExactlyInAnyOrder(kept, future);
        assertThat(schoolIds(tenant, teacher)).containsExactly(schoolKept);
        assertThat(classIds(tenant, assistant)).containsExactly(kept);
        assertThat(schoolIds(tenant, assistant)).containsExactly(schoolKept);
        assertThat(classIds(tenant, stranger)).isEmpty();
        assertThat(schoolIds(tenant, stranger)).isEmpty();

        // the homeroom teacher keeps the class whatever the schedule says
        write("update academic_classes set homeroom_teacher_id = ? where id = ?", stranger, lapsed);
        assertThat(classIds(tenant, stranger)).containsExactly(lapsed);
        assertThat(schoolIds(tenant, stranger)).containsExactly(schoolLapsed);
    }

    @Test
    void anotherSchoolsNewPeriodsDoNotRescueTheOldSchoolsAccess() {
        String tenant = id();
        String oldSchool = school(tenant, "THCS Cũ");
        String newSchool = school(tenant, "THCS Mới");
        String oldClass = classroom(tenant, oldSchool, "9A1");
        String newClass = classroom(tenant, newSchool, "9A2");
        String subject = subject(tenant, "Toán");
        String teacher = user(tenant, "Quốc Vương");
        event(tenant, batch(tenant, oldSchool, "PUBLISHED", "Đợt 1"), oldSchool, oldClass, subject, "PUBLISHED",
                sinceNow().minusDays(1), 1, teacher, null, null);
        event(tenant, batch(tenant, newSchool, "PUBLISHED", "Đợt 1"), newSchool, newClass, subject, "PUBLISHED",
                OffsetDateTime.now().plusDays(3), 1, teacher, null, null);

        assertThat(schoolIds(tenant, teacher)).containsExactly(newSchool);
        assertThat(classIds(tenant, teacher)).containsExactly(newClass);
    }

    @Test
    void draftPreviewMarksExistingNewAndLapsingAccess() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        String c9a2 = classroom(tenant, school, "9A2");
        String c9a4 = classroom(tenant, school, "9A4");
        String c9a7 = classroom(tenant, school, "9A7");
        String subject = subject(tenant, "Toán");
        String vuong = user(tenant, "Nguyễn Quốc Vương");
        String tuan = user(tenant, "Trần Thanh Tuấn");
        String admin = user(tenant, "Giáo vụ");
        String template = schedule.periodTemplates(tenant).get(0).id();
        // fixed clock = Thu 12/11/2026. Vuong: 9A2 finished Fri 30/10 (window starts Mon 26/10, lapses 16/11),
        // 9A4 still has a period next week.
        String published = batch(tenant, school, "PUBLISHED", "Đợt HK1");
        event(tenant, published, school, c9a2, subject, "PUBLISHED", at("2026-10-30", "13:30"), 6, vuong, null, null);
        event(tenant, published, school, c9a4, subject, "PUBLISHED", at("2026-11-17", "13:30"), 6, vuong, null, null);

        var result = schedule.saveDraft(tenant, new TeachingScheduleDraftInput(null, school, subject, null, "2026-11-16",
                "2026-11-22", template, null, List.of(
                new Row("r1", null, 3, 1, c9a4, null, null, vuong, null, null),
                new Row("r2", null, 4, 2, c9a7, null, null, vuong, tuan, null))), admin);

        Map<String, AccessChangeResponse> byTeacher = new HashMap<>();
        result.accessChanges().forEach(a -> byTeacher.put(a.teacherId(), a));
        AccessChangeResponse forVuong = byTeacher.get(vuong);
        assertThat(forVuong.schoolState()).isEqualTo("EXISTING");
        assertThat(forVuong.weeklyPeriods()).isEqualTo(2);
        assertThat(forVuong.lastPeriodDate()).isEqualTo("2026-11-19");
        assertThat(forVuong.classes()).extracting(c -> c.className() + ":" + c.state() + ":" + c.revokeOn())
                .containsExactly("9A4:EXISTING:null", "9A7:NEW:null", "9A2:REVOKE:2026-11-16");
        AccessChangeResponse forTuan = byTeacher.get(tuan);
        assertThat(forTuan.schoolState()).isEqualTo("NEW");
        assertThat(forTuan.weeklyPeriods()).isEqualTo(1);
        assertThat(forTuan.classes()).extracting(c -> c.className() + ":" + c.state()).containsExactly("9A7:NEW");
    }

    @Test
    void accessLookupDoesNotScanTheEventTableOn20kEvents() {
        String tenant = id();
        String school = school(tenant, "THCS Lớn");
        String batch = batch(tenant, school, "PUBLISHED", "Đợt 1");
        String subject = subject(tenant, "Toán");
        write("""
                insert into academic_classes (id, tenant_id, school_id, name, created_at, updated_at)
                select gen_random_uuid()::text, ?, ?, 'L' || g, now(), now() from generate_series(1, 500) g
                """, tenant, school);
        write("""
                insert into teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id,
                       start_at, end_at, weekday, period_start, period_count, status)
                select gen_random_uuid()::text, ?, ?, ?, c.id, ?, ts, ts + interval '45 minutes', 1, 1, 1, 'PUBLISHED'
                from generate_series(1, 20000) g
                join (select id, row_number() over (order by id) rn from academic_classes where tenant_id = ?) c on c.rn = 1 + g % 500,
                lateral (select timestamptz '2026-01-01 07:15+07' + (g * interval '10 minutes') ts) t
                """, tenant, batch, school, subject, tenant);
        write("""
                insert into users (id, tenant_id, email, full_name, status, created_at, updated_at)
                select md5(? || g), ?, md5(? || g) || '@example.test', 'GV ' || g, 'active', now(), now()
                from generate_series(0, 299) g
                """, tenant, tenant, tenant);
        write("""
                insert into teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role)
                select gen_random_uuid()::text, tenant_id, id, md5(? || ((row_number() over ())::int % 300)), 'MAIN'
                from teaching_schedule_events where tenant_id = ?
                """, tenant, tenant);
        jdbc.execute("analyze teaching_schedule_events");
        jdbc.execute("analyze teaching_schedule_event_teachers");

        String teacher = jdbc.queryForObject("select teacher_user_id from teaching_schedule_event_teachers where tenant_id = ? limit 1",
                String.class, tenant);
        List<String> plan = jdbc.queryForList("""
                explain select distinct c.school_id from academic_classes c
                where c.tenant_id = ? and (c.homeroom_teacher_id = ? or exists (
                    select 1 from teaching_schedule_events e
                    where e.tenant_id = c.tenant_id and e.academic_class_id = c.id and e.status = 'PUBLISHED'
                      and e.end_at >= now() - interval '14 days'
                      and exists (select 1 from teaching_schedule_event_teachers l where l.event_id = e.id and l.teacher_user_id = ?)))
                """, String.class, tenant, teacher, teacher);

        System.out.println("ACCESS PLAN" + System.lineSeparator() + String.join(System.lineSeparator(), plan));
        assertThat(String.join(" ", plan)).doesNotContain("Seq Scan on teaching_schedule_events");
    }
}
