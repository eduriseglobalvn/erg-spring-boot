package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingScheduleResponses.OverviewCellResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.OverviewGridResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolPageResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherPageResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherRowResponse;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** B8: weekly overview by school and by teacher, the 3-school grid and the load settings, on a real database. */
class TeachingScheduleOverviewIT extends TeachingScheduleITBase {

    @Autowired TeachingScheduleOperations schedule;

    private static final LocalDate WEEK = LocalDate.of(2026, 11, 9);

    private String tenant;
    private String tcd;
    private String nguyenDu;
    private String vanLang;
    private String moi;
    private String vuong;
    private String lan;
    private String tuan;
    private String ngoc;
    private String hamy;
    private String draftEvent;
    private String publishedEvent;
    private String morningEvent;
    private String travelEvent;

    /**
     * Week of Mon 09/11/2026. Vuong is double booked on Wed 11/11 period 6 (draft at TCD, published at Nguyen Du, Ngoc
     * assists the draft one). Lan has Van Lang period 2 then Nguyen Du period 3, only 30 minutes apart. Tuan has two
     * quiet periods at TCD. "Moi" has a class but no period; Ha My teaches nowhere.
     */
    private void setUp() {
        tenant = id();
        tcd = school(tenant, "THCS Trương Công Định");
        nguyenDu = school(tenant, "THCS Nguyễn Du");
        vanLang = school(tenant, "THCS Văn Lang");
        moi = school(tenant, "THCS Mới");
        classroom(tenant, moi, "5A1");
        String c9a4 = classroom(tenant, tcd, "9A4");
        String c9a5 = classroom(tenant, tcd, "9A5");
        String c8a2 = classroom(tenant, nguyenDu, "8A2");
        String c6a3 = classroom(tenant, nguyenDu, "6A3");
        String c7a1 = classroom(tenant, vanLang, "7A1");
        String subject = subject(tenant, "Toán");
        vuong = instructor(tenant, "Nguyễn Quốc Vương", tcd);
        lan = instructor(tenant, "Trần Lan Anh", vanLang);
        tuan = instructor(tenant, "Lê Thanh Tuấn", tcd);
        ngoc = instructor(tenant, "Bảo Ngọc", tcd);
        hamy = instructor(tenant, "Phạm Hà My", tcd);

        String tcdPublished = batch(tenant, tcd, "PUBLISHED", "Đợt HK1");
        String tcdDraft = batch(tenant, tcd, "DRAFT", "Đợt 3");
        String ndPublished = batch(tenant, nguyenDu, "PUBLISHED", "Đợt HK1");
        String vlPublished = batch(tenant, vanLang, "PUBLISHED", "Đợt HK1");
        publishedEvent = event(tenant, ndPublished, nguyenDu, c8a2, subject, "PUBLISHED", at("2026-11-11", "13:30"), 6, vuong, null, null);
        draftEvent = event(tenant, tcdDraft, tcd, c9a4, subject, "DRAFT", at("2026-11-11", "13:30"), 6, vuong, ngoc, null);
        morningEvent = event(tenant, vlPublished, vanLang, c7a1, subject, "PUBLISHED", at("2026-11-10", "08:00"), 2, lan, null, null);
        travelEvent = event(tenant, ndPublished, nguyenDu, c6a3, subject, "PUBLISHED", at("2026-11-10", "09:15"), 3, lan, null, null);
        event(tenant, tcdPublished, tcd, c9a4, subject, "PUBLISHED", at("2026-11-12", "07:15"), 1, tuan, null, null);
        event(tenant, tcdPublished, tcd, c9a5, subject, "PUBLISHED", at("2026-11-12", "10:00"), 4, tuan, null, null);
        // Ha My last taught at TCD on Fri 30/10: her access to it ends on Mon 16/11
        event(tenant, tcdPublished, tcd, c9a5, subject, "PUBLISHED", at("2026-10-30", "13:30"), 6, hamy, null, null);
    }

    private SchoolPageResponse schools(String search, String quick) {
        return schedule.overviewSchools(tenant, null, WEEK, search, quick, 0, 50);
    }

    @Test
    void schoolListPutsConflictsFirstAndCountsEveryChip() {
        setUp();

        SchoolPageResponse page = schools(null, null);

        assertThat(page.items()).extracting(r -> r.schoolShortName() + ":" + r.conflictCount() + ":" + r.hasDraft() + ":" + r.periodCount())
                .containsExactly("TCĐ:1:true:3", "Nguyễn Du:2:false:2", "Văn Lang:1:false:1", "Mới:0:false:0");
        assertThat(page.total()).isEqualTo(4);
        assertThat(page.quickCounts().conflicts()).isEqualTo(3);
        assertThat(page.quickCounts().travel()).isEqualTo(2);
        assertThat(page.quickCounts().drafts()).isEqualTo(1);
        assertThat(page.quickCounts().empty()).isEqualTo(1);

        assertThat(schools(null, "TRAVEL").items()).extracting("schoolShortName").containsExactly("Nguyễn Du", "Văn Lang");
        assertThat(schools(null, "EMPTY").items()).extracting("schoolShortName").containsExactly("Mới");
        assertThat(schools(null, "DRAFT").items()).extracting("schoolShortName").containsExactly("TCĐ");
        // search reaches class and teacher names; chip counts do not follow the filters
        assertThat(schools("9a5", null).items()).extracting("schoolShortName").containsExactly("TCĐ");
        assertThat(schools("lan anh", null).items()).extracting("schoolShortName").containsExactlyInAnyOrder("Văn Lang", "Nguyễn Du");
        assertThat(schools("9a5", null).quickCounts().conflicts()).isEqualTo(3);
        // paging keeps the order and the total
        SchoolPageResponse second = schedule.overviewSchools(tenant, null, WEEK, null, null, 1, 3);
        assertThat(second.total()).isEqualTo(4);
        assertThat(second.items()).extracting("schoolShortName").containsExactly("Mới");
    }

    @Test
    void aSchoolOutsideTheCallersScopeIsNotListed() {
        setUp();

        SchoolPageResponse page = schedule.overviewSchools(tenant, List.of(tcd), WEEK, null, null, 0, 50);

        assertThat(page.items()).extracting("schoolShortName").containsExactly("TCĐ");
    }

    @Test
    void gridShowsEveryPeriodWithItsConflictKindsAndRefusesFourSchools() {
        setUp();
        write("insert into tk_holidays (id, tenant_id, holiday_date, name) values (?, ?, date '2026-11-13', 'Nghỉ bù')", id(), tenant);

        OverviewGridResponse grid = schedule.overviewGrid(tenant, WEEK, List.of(tcd, nguyenDu));

        assertThat(grid.weekStart()).isEqualTo("2026-11-09");
        assertThat(grid.template().slots()).hasSize(10);
        assertThat(grid.schools()).extracting("schoolShortName").containsExactly("TCĐ", "Nguyễn Du");
        assertThat(grid.holidays()).extracting("date", "name").containsExactly(org.assertj.core.groups.Tuple.tuple("2026-11-13", "Nghỉ bù"));
        Map<String, OverviewCellResponse> byEvent = grid.cells().stream().collect(Collectors.toMap(OverviewCellResponse::eventId, Function.identity()));
        assertThat(grid.cells()).hasSize(5);
        OverviewCellResponse draft = byEvent.get(draftEvent);
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.batchLabel()).isEqualTo("Đợt 3");
        assertThat(draft.className()).isEqualTo("9A4");
        assertThat(draft.teacherName()).isEqualTo("Q.Vương");
        assertThat(draft.assistantName()).isEqualTo("Bảo Ngọc");
        assertThat(draft.conflictKinds()).containsExactly("TEACHER_TIME");
        assertThat(draft.weekday()).isEqualTo(3);
        assertThat(draft.periodNo()).isEqualTo(6);
        assertThat(byEvent.get(publishedEvent).conflictKinds()).containsExactly("TEACHER_TIME");
        assertThat(byEvent.get(travelEvent).conflictKinds()).containsExactly("TRAVEL_TIME");
        assertThat(grid.conflicts()).extracting("kind").containsExactlyInAnyOrder("TEACHER_TIME", "TRAVEL_TIME");

        assertThatThrownBy(() -> schedule.overviewGrid(tenant, WEEK, List.of(tcd, nguyenDu, vanLang, moi)))
                .isInstanceOfSatisfying(TeachingScheduleException.class, e -> assertThat(e.getCode()).isEqualTo("TOO_MANY_SCHOOLS"));
    }

    @Test
    void teacherRowsCarryLoadSlotsTravelAndTheQuickFilters() {
        setUp();

        TeacherPageResponse page = schedule.overviewTeachers(tenant, null, WEEK, null, null, null, 0, 50);

        Map<String, TeacherRowResponse> rows = page.items().stream().collect(Collectors.toMap(TeacherRowResponse::teacherId, Function.identity()));
        assertThat(page.total()).isEqualTo(5);
        TeacherRowResponse forVuong = rows.get(vuong);
        assertThat(forVuong.weeklyPeriods()).isEqualTo(2);
        assertThat(forVuong.schoolCount()).isEqualTo(2);
        assertThat(forVuong.conflictCount()).isEqualTo(1);
        assertThat(forVuong.maxPeriods()).isEqualTo(24);
        assertThat(forVuong.initials()).isEqualTo("QV");
        assertThat(forVuong.slots()).hasSize(1);
        assertThat(forVuong.slots().get(0).eventIds()).containsExactlyInAnyOrder(draftEvent, publishedEvent);
        assertThat(forVuong.slots().get(0).conflict()).isTrue();
        assertThat(forVuong.slots().get(0).status()).isEqualTo("DRAFT");
        assertThat(forVuong.days().get(2).multiSchoolSameSession()).isTrue();

        TeacherRowResponse forLan = rows.get(lan);
        assertThat(forLan.conflictCount()).isZero();
        assertThat(forLan.days().get(1).schoolShortNames()).containsExactly("Văn Lang", "Nguyễn Du");
        assertThat(forLan.days().get(1).travel().gapMinutes()).isEqualTo(30);
        assertThat(forLan.days().get(1).travel().requiredMinutes()).isEqualTo(50);
        assertThat(forLan.days().get(1).travel().fromSchoolShortName()).isEqualTo("VL");
        assertThat(forLan.days().get(1).travel().toSchoolShortName()).isEqualTo("ND");
        assertThat(forLan.slots()).extracting(s -> s.periodNo() + ":" + s.travel()).containsExactly("2:false", "3:true");

        // the assistant's period counts in her load and says so, but is not a clash of her own
        TeacherRowResponse forNgoc = rows.get(ngoc);
        assertThat(forNgoc.weeklyPeriods()).isEqualTo(1);
        assertThat(forNgoc.slots().get(0).roles()).containsExactly("ASSISTANT");
        assertThat(forNgoc.slots().get(0).conflict()).isFalse();
        assertThat(forNgoc.conflictCount()).isZero();

        assertThat(rows.get(hamy).weeklyPeriods()).isZero();
        assertThat(rows.get(hamy).revokeNotice().schoolId()).isEqualTo(tcd);
        assertThat(rows.get(hamy).revokeNotice().schoolState()).isEqualTo("REVOKE");
        assertThat(rows.get(hamy).revokeNotice().schoolRevokeOn()).isEqualTo("2026-11-16");
        assertThat(rows.get(hamy).revokeNotice().classes()).extracting(c -> c.className() + ":" + c.state()).containsExactly("9A5:REVOKE");
        assertThat(rows.get(tuan).revokeNotice()).isNull();
        assertThat(page.items().get(0).teacherId()).isIn(vuong, lan, tuan);
        assertThat(page.quickCounts().conflicts()).isEqualTo(1);
        assertThat(page.quickCounts().travel()).isEqualTo(1);
        assertThat(page.quickCounts().multiSchool()).isEqualTo(2);
        assertThat(page.quickCounts().overload()).isZero();
        assertThat(page.quickCounts().empty()).isEqualTo(1);
        assertThat(schedule.overviewTeachers(tenant, null, WEEK, null, "TRAVEL", null, 0, 50).items())
                .extracting(TeacherRowResponse::teacherId).containsExactly(lan);
        assertThat(schedule.overviewTeachers(tenant, null, WEEK, "hà my", "EMPTY", null, 0, 50).items())
                .extracting(TeacherRowResponse::teacherId).containsExactly(hamy);
        assertThat(schedule.overviewTeachers(tenant, null, WEEK, null, null, "LOAD_ASC", 0, 50).items().get(0).weeklyPeriods()).isZero();
    }

    @Test
    void overloadFollowsTheTenantLoadSettingsAndInvalidSettingsAreRefused() {
        setUp();
        assertThat(schedule.loadSettings(tenant).loadWarn()).isEqualTo(18);
        assertThat(schedule.loadSettings(tenant).loadMax()).isEqualTo(24);
        assertThat(schedule.loadSettings(tenant).updatedAt()).isNull();

        assertThatThrownBy(() -> schedule.updateLoadSettings(tenant, 25, 24, id())).isInstanceOfSatisfying(TeachingScheduleException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_LOAD_SETTINGS"));
        assertThatThrownBy(() -> schedule.updateLoadSettings(tenant, 0, 24, id())).isInstanceOfSatisfying(TeachingScheduleException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_LOAD_SETTINGS"));
        assertThatThrownBy(() -> schedule.updateLoadSettings(tenant, 10, 61, id())).isInstanceOfSatisfying(TeachingScheduleException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_LOAD_SETTINGS"));

        assertThat(schedule.updateLoadSettings(tenant, 1, 2, null).loadMax()).isEqualTo(2);
        TeacherPageResponse page = schedule.overviewTeachers(tenant, null, WEEK, null, "OVERLOAD", null, 0, 50);
        assertThat(page.items()).extracting(TeacherRowResponse::teacherId).containsExactlyInAnyOrder(vuong, tuan, lan);
        assertThat(page.items().get(0).maxPeriods()).isEqualTo(2);
        assertThat(schedule.loadSettings(tenant).updatedAt()).isNotNull();
    }

    @Test
    void draftsOfDifferentBatchesDoNotClashInTheOverview() {
        tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        String classId = classroom(tenant, school, "9A1");
        String subject = subject(tenant, "Toán");
        String teacher = instructor(tenant, "Nguyễn Quốc Vương", school);
        event(tenant, batch(tenant, school, "DRAFT", "Đợt 1"), school, classId, subject, "DRAFT", at("2026-11-11", "07:15"), 1, teacher, null, null);
        event(tenant, batch(tenant, school, "DRAFT", "Đợt 2"), school, classId, subject, "DRAFT", at("2026-11-11", "07:15"), 1, teacher, null, null);

        SchoolPageResponse page = schools(null, null);

        assertThat(page.items().get(0).conflictCount()).isZero();
        assertThat(page.items().get(0).periodCount()).isEqualTo(2);
    }

    @Test
    void aHundredAndTwentyEightSchoolsAnswerInUnderASecond() {
        String big = id();
        write("""
                insert into education_units (id, tenant_id, name, created_at, updated_at)
                select md5(? || 's' || s), ?, 'THCS Trường ' || lpad(s::text, 3, '0'), now(), now() from generate_series(1, 128) s
                """, big, big);
        write("""
                insert into academic_classes (id, tenant_id, school_id, name, created_at, updated_at)
                select md5(? || 'c' || s || '-' || k), ?, md5(? || 's' || s), 'L' || k, now(), now()
                from generate_series(1, 128) s, generate_series(0, 17) k
                """, big, big, big);
        write("""
                insert into users (id, tenant_id, email, full_name, status, created_at, updated_at)
                select md5(? || 'u' || t), ?, md5(? || 'u' || t) || '@example.test', 'Giáo viên ' || t, 'active', now(), now()
                from generate_series(1, 64) t
                """, big, big, big);
        write("""
                insert into teaching_schedule_batches (id, tenant_id, school_id, apply_from, repeat_weeks, status, label)
                select md5(? || 'b' || s), ?, md5(? || 's' || s), date '2026-11-09', 10, 'PUBLISHED', 'Đợt 1' from generate_series(1, 128) s
                """, big, big, big);
        String subject = subject(big, "Toán");
        // school s uses teacher ((s-1) % 64) + 1; schools 1-64 teach Mon-Wed and schools 65-128 Thu-Sat, so no teacher clashes
        write("""
                insert into teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id,
                       start_at, end_at, weekday, period_start, period_count, status)
                select md5(? || 'e' || s || '-' || k), ?, md5(? || 'b' || s), md5(? || 's' || s), md5(? || 'c' || s || '-' || k), ?,
                       ts, ts + interval '45 minutes', wd, 1 + k % 6, 1, 'PUBLISHED'
                from (select s, k, (case when s <= 64 then 1 else 4 end) + k / 6 wd from generate_series(1, 128) s, generate_series(0, 17) k) g,
                     lateral (select timestamptz '2026-11-09 00:00+07' + ((wd - 1) * interval '1 day') + ((7 + k % 6) * interval '1 hour') ts) t
                """, big, big, big, big, big, subject);
        write("""
                insert into teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role)
                select gen_random_uuid()::text, ?, md5(? || 'e' || s || '-' || k), md5(? || 'u' || (((s - 1) % 64) + 1)), 'MAIN'
                from generate_series(1, 128) s, generate_series(0, 17) k
                """, big, big, big);

        schedule.overviewSchools(big, null, WEEK, null, null, 0, 200);
        schedule.overviewTeachers(big, null, WEEK, null, null, null, 0, 200);
        long start = System.nanoTime();
        SchoolPageResponse schoolPage = schedule.overviewSchools(big, null, WEEK, null, null, 0, 200);
        long schoolsMs = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        TeacherPageResponse teacherPage = schedule.overviewTeachers(big, null, WEEK, null, null, null, 0, 200);
        long teachersMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("OVERVIEW 128 schools: schools=" + schoolsMs + "ms teachers=" + teachersMs + "ms");
        assertThat(schoolPage.total()).isEqualTo(128);
        assertThat(schoolPage.quickCounts().conflicts()).isZero();
        assertThat(teacherPage.total()).isEqualTo(64);
        assertThat(teacherPage.items().get(0).weeklyPeriods()).isEqualTo(36);
        assertThat(schoolsMs).isLessThan(1000);
        assertThat(teachersMs).isLessThan(1000);
    }
}
