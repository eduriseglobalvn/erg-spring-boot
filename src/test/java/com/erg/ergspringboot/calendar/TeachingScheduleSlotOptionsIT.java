package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotOptionsResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotTeacherOption;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** B6: the free / busy / too-far / on-leave state of every class, teacher and room for one grid cell. */
class TeachingScheduleSlotOptionsIT extends TeachingScheduleITBase {

    @Autowired TeachingScheduleOperations schedule;

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 18);

    private String tenant;
    private String tcd;
    private String c9a4;
    private String c9a5;
    private String c9a6;
    private String tuan;
    private String hamy;
    private String vuong;
    private String lan;
    private String thu;
    private String ngoc;
    private String busyEvent;

    /**
     * Cell: THCS TCD, Wed 18/11 period 6 (13:30-14:15), class 9A4. Last week (11/11, same period) Tuan taught 9A4 in
     * PM 03 and Ha My taught 9A6 there. This week: 9A5 is busy in PM 02 (Vuong teaches it at Nguyen Du at the same
     * time), Lan ends a period at Nguyen Du at 13:00, Thu is on leave, and Tuan has period 7 at TCD right after.
     */
    private void setUp() {
        tenant = id();
        tcd = school(tenant, "THCS Trương Công Định");
        String nguyenDu = school(tenant, "THCS Nguyễn Du");
        c9a4 = classroom(tenant, tcd, "9A4");
        c9a5 = classroom(tenant, tcd, "9A5");
        c9a6 = classroom(tenant, tcd, "9A6");
        String c9a7 = classroom(tenant, tcd, "9A7");
        String c8a1 = classroom(tenant, tcd, "8A1");
        String c8a2 = classroom(tenant, nguyenDu, "8A2");
        write("update academic_classes set grade = 'Khối 9' where id in (?, ?, ?, ?)", c9a4, c9a5, c9a6, c9a7);
        write("update academic_classes set grade = '8' where id in (?, ?)", c8a1, c8a2);
        String subject = subject(tenant, "Toán");
        tuan = instructor(tenant, "Lê Thanh Tuấn", tcd);
        hamy = instructor(tenant, "Phạm Hà My", tcd);
        vuong = instructor(tenant, "Nguyễn Quốc Vương", tcd);
        lan = instructor(tenant, "Trần Lan Anh", tcd);
        thu = instructor(tenant, "Đặng Thu Trang", tcd);
        ngoc = instructor(tenant, "Bảo Ngọc", tcd);
        String pm03 = room(tenant, tcd, "PM 03");
        String pm02 = room(tenant, tcd, "PM 02");
        room(tenant, tcd, "P.Tin 1");
        write("update teaching_schedule_rooms set seat_count = 40 where id = ?", pm03);

        String published = batch(tenant, tcd, "PUBLISHED", "Đợt HK1");
        String ndPublished = batch(tenant, nguyenDu, "PUBLISHED", "Đợt HK1");
        event(tenant, published, tcd, c9a4, subject, "PUBLISHED", at("2026-11-04", "13:30"), 6, tuan, null, pm03);
        event(tenant, published, tcd, c9a4, subject, "PUBLISHED", at("2026-11-11", "13:30"), 6, tuan, null, pm03);
        event(tenant, published, tcd, c9a6, subject, "PUBLISHED", at("2026-11-11", "13:30"), 6, hamy, null, null);
        busyEvent = event(tenant, published, tcd, c9a5, subject, "PUBLISHED", at("2026-11-18", "13:30"), 6, null, null, pm02);
        event(tenant, ndPublished, nguyenDu, c8a2, subject, "PUBLISHED", at("2026-11-18", "13:30"), 6, vuong, null, null);
        event(tenant, ndPublished, nguyenDu, c8a2, subject, "PUBLISHED", at("2026-11-18", "12:15"), 5, lan, null, null);
        event(tenant, published, tcd, c9a6, subject, "PUBLISHED", at("2026-11-18", "14:15"), 7, tuan, null, null);
        write("insert into tk_requests (id, tenant_id, school_id, code, user_id, type, leave_kind, from_date, to_date, part, days,"
                + " status) values (?, ?, ?, 'NP-1', ?, 'LEAVE', 'ANNUAL', date '2026-11-18', date '2026-11-18', 'FULL', 1, 'APPROVED')",
                id(), tenant, tcd, thu);
    }

    private SlotOptionsResponse options(String excludeEventId) {
        return schedule.slotOptions(tenant, tcd, null, WEDNESDAY, 6, c9a4, excludeEventId);
    }

    @Test
    void classesAreFilteredToTheGradeAndMarkedBusyOrSuggested() {
        setUp();

        SlotOptionsResponse result = options(null);

        assertThat(result.schoolName()).isEqualTo("THCS Trương Công Định");
        assertThat(result.grade()).isEqualTo(9);
        assertThat(result.classes()).extracting(c -> c.className() + ":" + c.state())
                .containsExactly("9A4:SUGGESTED", "9A6:SUGGESTED", "9A7:FREE", "9A5:BUSY");
        assertThat(result.classes().get(3).busy().className()).isEqualTo("9A5");
        assertThat(result.classes().get(3).busy().roomName()).isEqualTo("PM 02");
        // moving the busy period itself away frees its class
        assertThat(options(busyEvent).classes()).extracting(c -> c.className() + ":" + c.state())
                .containsExactly("9A4:SUGGESTED", "9A6:SUGGESTED", "9A5:FREE", "9A7:FREE");
    }

    @Test
    void teachersAreRankedFreeThenBusyThenTooFarThenOnLeaveWithTheReason() {
        setUp();

        SlotOptionsResponse result = options(null);

        assertThat(result.teachers()).extracting(t -> t.teacherId())
                .containsExactly(hamy, tuan, ngoc, vuong, lan, thu);
        SlotTeacherOption forTuan = result.teachers().get(1);
        assertThat(forTuan.state()).isEqualTo("SUGGESTED");
        assertThat(forTuan.taughtHereLastWeek()).isTrue();
        assertThat(forTuan.weeklyPeriods()).isEqualTo(1);
        assertThat(forTuan.sameSchoolNeighbor().className()).isEqualTo("9A6");
        assertThat(forTuan.sameSchoolNeighbor().periodNo()).isEqualTo(7);
        assertThat(forTuan.classAccess()).isEqualTo("EXISTING");
        assertThat(forTuan.initials()).isEqualTo("TT");
        assertThat(result.teachers().get(0).classAccess()).isEqualTo("NEW");
        assertThat(result.teachers().get(2).state()).isEqualTo("FREE");

        SlotTeacherOption forVuong = result.teachers().get(3);
        assertThat(forVuong.state()).isEqualTo("BUSY");
        assertThat(forVuong.busy().schoolShortName()).isEqualTo("Nguyễn Du");
        assertThat(forVuong.busy().className()).isEqualTo("8A2");

        SlotTeacherOption forLan = result.teachers().get(4);
        assertThat(forLan.state()).isEqualTo("TRAVEL");
        assertThat(forLan.travel().gapMinutes()).isEqualTo(30);
        assertThat(forLan.travel().requiredMinutes()).isEqualTo(50);
        assertThat(forLan.travel().fromSchoolShortName()).isEqualTo("ND");
        assertThat(forLan.travel().toSchoolShortName()).isEqualTo("TCĐ");

        SlotTeacherOption forThu = result.teachers().get(5);
        assertThat(forThu.state()).isEqualTo("LEAVE");
        assertThat(forThu.leave().part()).isEqualTo("FULL");
        assertThat(forThu.leave().fromDate()).isEqualTo("2026-11-18");
    }

    @Test
    void roomsKeepTheUsualOneFirstAndMarkTheBusyOne() {
        setUp();

        SlotOptionsResponse result = options(null);

        assertThat(result.rooms()).extracting(r -> r.roomName() + ":" + r.state() + ":" + r.usualForClass())
                .containsExactly("PM 03:SUGGESTED:true", "P.Tin 1:FREE:false", "PM 02:BUSY:false");
        assertThat(result.rooms().get(0).seatCount()).isEqualTo(40);
        assertThat(result.rooms().get(2).busy().className()).isEqualTo("9A5");
    }

    @Test
    void aPeriodWithoutAClockTimeCannotBeAsked() {
        setUp();

        assertThatThrownBy(() -> schedule.slotOptions(tenant, tcd, null, WEDNESDAY, 5, c9a4, null))
                .isInstanceOfSatisfying(TeachingScheduleException.class, e -> assertThat(e.getCode()).isEqualTo("PERIOD_HAS_NO_TIME"));
    }

    @Test
    void aTenantOf200TeachersAnswersInUnderThreeHundredMilliseconds() {
        String big = id();
        String school = school(big, "THCS Lớn");
        write("""
                insert into academic_classes (id, tenant_id, school_id, name, grade, created_at, updated_at)
                select md5(? || 'c' || k), ?, ?, 'L' || k, '9', now(), now() from generate_series(1, 40) k
                """, big, big, school);
        write("""
                insert into users (id, tenant_id, email, full_name, status, created_at, updated_at)
                select md5(? || 'u' || t), ?, md5(? || 'u' || t) || '@example.test', 'Giáo viên ' || t, 'active', now(), now()
                from generate_series(1, 200) t
                """, big, big, big);
        write("""
                insert into user_access_scopes (id, user_id, education_unit_id, role, created_at, updated_at)
                select md5(? || 'a' || t), md5(? || 'u' || t), ?, 'lms.instructor', now(), now() from generate_series(1, 200) t
                """, big, big, school);
        String batch = batch(big, school, "PUBLISHED", "Đợt 1");
        String subject = subject(big, "Toán");
        write("""
                insert into teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id,
                       start_at, end_at, weekday, period_start, period_count, status)
                select md5(? || 'e' || t), ?, ?, ?, md5(? || 'c' || (1 + t % 40)), ?,
                       timestamptz '2026-11-16 07:15+07' + ((t % 6) * interval '1 day') + ((t % 4) * interval '1 hour'),
                       timestamptz '2026-11-16 08:00+07' + ((t % 6) * interval '1 day') + ((t % 4) * interval '1 hour'),
                       1 + t % 6, 1 + t % 4, 1, 'PUBLISHED'
                from generate_series(1, 200) t
                """, big, big, batch, school, big, subject);
        write("""
                insert into teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role)
                select md5(? || 'l' || t), ?, md5(? || 'e' || t), md5(? || 'u' || t), 'MAIN' from generate_series(1, 200) t
                """, big, big, big, big);

        schedule.slotOptions(big, school, null, WEDNESDAY, 2, null, null);
        long start = System.nanoTime();
        SlotOptionsResponse result = schedule.slotOptions(big, school, null, WEDNESDAY, 2, null, null);
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("SLOT OPTIONS 200 teachers, 40 classes: " + millis + "ms");
        assertThat(result.teachers()).hasSize(200);
        assertThat(result.classes()).hasSize(40);
        assertThat(millis).isLessThan(300);
    }
}
