package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingScheduleResponses.DraftResultResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ReusePreviewResponse;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** B7: "Dùng lại tuần" with the numbers of the board (18 source periods, 11 / 6 / 0, 16 / 0 / 1, 17 / 0 / 0). */
class TeachingScheduleReuseIT extends TeachingScheduleITBase {

    @Autowired TeachingScheduleOperations schedule;

    private static final Map<Integer, String> START = Map.of(1, "07:15", 2, "08:00", 3, "09:15", 4, "10:00", 6, "13:30", 7, "14:15");

    private String tenant;
    private String school;
    private String tuan;
    private String vuong;
    private String ngoc;
    private String admin;

    private int classNo;
    private String batch;
    private String subject;

    private void addPeriod(String date, int period, String teacher, String assistant, String room) {
        String classId = classroom(tenant, school, "9A" + (++classNo));
        event(tenant, batch, school, classId, subject, "PUBLISHED", at(date, START.get(period)), period, teacher, assistant, room);
    }

    /**
     * Source week Mon 09/11/2026 at TCD: 18 published periods (Tuan 7, Vuong 11). Wed 11/11 period 6 of Vuong clashes
     * with his published period at Van Lang, so it must not be copied. 20/11 is a holiday. A makeup period of the
     * class that has Vuong's Tue 10/11 period 1 already sits on Tue 24/11 in another batch.
     */
    private void setUp() {
        tenant = id();
        school = school(tenant, "THCS Trương Công Định");
        String vanLang = school(tenant, "THCS Văn Lang");
        subject = subject(tenant, "Toán");
        tuan = user(tenant, "Lê Thanh Tuấn");
        vuong = user(tenant, "Nguyễn Quốc Vương");
        ngoc = user(tenant, "Bảo Ngọc");
        admin = user(tenant, "Giáo vụ");
        String pm03 = room(tenant, school, "PM 03");
        batch = batch(tenant, school, "PUBLISHED", "Đợt HK1");
        classNo = 0;
        // Friday 13/11: six periods that the holiday of 20/11 will swallow
        for (int p : new int[] {1, 2, 3}) addPeriod("2026-11-13", p, tuan, null, null);
        for (int p : new int[] {4, 6, 7}) addPeriod("2026-11-13", p, vuong, null, null);
        // the clashing period
        addPeriod("2026-11-11", 6, vuong, null, null);
        // Monday: Tuan 4 periods (the first has a room and an assistant), Vuong 2
        addPeriod("2026-11-09", 1, tuan, ngoc, pm03);
        for (int p : new int[] {2, 3, 4}) addPeriod("2026-11-09", p, tuan, null, null);
        for (int p : new int[] {6, 7}) addPeriod("2026-11-09", p, vuong, null, null);
        // Tuesday: Vuong 4 periods; Thursday: Vuong 1
        for (int p : new int[] {1, 2, 3, 4}) addPeriod("2026-11-10", p, vuong, null, null);
        addPeriod("2026-11-12", 1, vuong, null, null);

        String vlBatch = batch(tenant, vanLang, "PUBLISHED", "Đợt HK1");
        event(tenant, vlBatch, vanLang, classroom(tenant, vanLang, "7A1"), subject, "PUBLISHED", at("2026-11-11", "13:30"), 6, vuong, null, null);
        write("insert into tk_holidays (id, tenant_id, holiday_date, name) values (?, ?, date '2026-11-20', 'Ngày Nhà giáo Việt Nam')", id(), tenant);
        String makeup = batch(tenant, school, "PUBLISHED", "Đợt bù T11");
        String tuesdayClass = jdbc.queryForObject("select academic_class_id from teaching_schedule_events where batch_id = ?"
                + " and start_at = ?::timestamptz and school_id = ?", String.class, batch, at("2026-11-10", "07:15").toString(), school);
        event(tenant, makeup, school, tuesdayClass, subject, "PUBLISHED", at("2026-11-24", "07:15"), 1, null, null, null);
    }

    private TeachingScheduleReuseInput input(String mode, List<String> weeks, String until, boolean skipHolidays, boolean keep) {
        return new TeachingScheduleReuseInput(school, "2026-11-09", mode, weeks, until, skipHolidays, keep);
    }

    @Test
    void previewCountsWhatIsCreatedSkippedAndBlockedPerWeek() {
        setUp();

        ReusePreviewResponse preview = schedule.previewReuse(tenant,
                input("SELECTED_WEEKS", List.of("2026-11-30", "2026-11-16", "2026-11-23"), null, true, true));

        assertThat(preview.sourcePeriods()).isEqualTo(18);
        assertThat(preview.perTeacher()).extracting(t -> t.teacherName() + ":" + t.periods()).containsExactly("Q.Vương:11", "T.Tuấn:7");
        assertThat(preview.excludedConflicts()).hasSize(1);
        assertThat(preview.excludedConflicts().get(0).kind()).isEqualTo("TEACHER_TIME");
        assertThat(preview.weeks()).extracting(w -> w.weekStart() + ":" + w.toCreate() + "/" + w.skipped() + "/" + w.conflicts())
                .containsExactly("2026-11-16:11/6/0", "2026-11-23:16/0/1", "2026-11-30:17/0/0");
        assertThat(preview.weeks().get(0).weekEnd()).isEqualTo("2026-11-21");
        assertThat(preview.weeks().get(0).holidays()).extracting("date", "name")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("2026-11-20", "Ngày Nhà giáo Việt Nam"));
        assertThat(preview.weeks().get(1).conflictDetails()).extracting("kind").containsExactly("CLASS_TIME");
        assertThat(preview.totalToCreate()).isEqualTo(44);
        // a preview writes nothing
        assertThat(jdbc.queryForObject("select count(*) from teaching_schedule_batches where tenant_id = ?", Integer.class, tenant)).isEqualTo(3);
    }

    @Test
    void reuseCreatesOneDraftBatchWithTheNonClashingPeriods() {
        setUp();

        DraftResultResponse result = schedule.reuseWeek(tenant,
                input("SELECTED_WEEKS", List.of("2026-11-16", "2026-11-23", "2026-11-30"), null, true, true), admin);

        assertThat(result.batch().status()).isEqualTo("DRAFT");
        assertThat(result.batch().label()).isEqualTo("Đợt 1");
        assertThat(result.batch().applyFrom()).isEqualTo("2026-11-16");
        assertThat(result.batch().repeatWeeks()).isEqualTo(3);
        assertThat(result.batch().affectedEventCount()).isEqualTo(44);
        assertThat(result.rowIdMap()).hasSize(17);
        String id = result.batch().id();
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and status = 'DRAFT'", id)).isEqualTo(44);
        assertThat(count("select count(*) from teaching_schedule_event_teachers t join teaching_schedule_events e on e.id = t.event_id"
                + " where e.batch_id = ? and t.role = 'MAIN'", id)).isEqualTo(44);
        // the copy keeps the room and the assistant of the one period that had them, once per week
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and room_id is not null", id)).isEqualTo(3);
        assertThat(count("select count(*) from teaching_schedule_event_teachers t join teaching_schedule_events e on e.id = t.event_id"
                + " where e.batch_id = ? and t.role = 'ASSISTANT'", id)).isEqualTo(3);
        // nothing lands on the holiday, and the clashing period of the source week is not copied anywhere
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and start_at::date = date '2026-11-20'", id)).isZero();
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and weekday = 3 and period_start = 6", id)).isZero();
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and subject_id = ?", id, subject)).isEqualTo(44);
        assertThat(result.accessChanges()).extracting(a -> a.teacherId()).containsExactlyInAnyOrder(tuan, vuong, ngoc);
        // the source batch is untouched
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ?", batch)).isEqualTo(18);
    }

    @Test
    void repeatUntilWithoutKeepingRoomAndAssistantDropsThemAndKeepsHolidays() {
        setUp();

        DraftResultResponse result = schedule.reuseWeek(tenant, input("REPEAT_UNTIL", null, "2026-11-22", false, false), admin);

        String id = result.batch().id();
        assertThat(result.batch().affectedEventCount()).isEqualTo(17);
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and room_id is not null", id)).isZero();
        assertThat(count("select count(*) from teaching_schedule_event_teachers t join teaching_schedule_events e on e.id = t.event_id"
                + " where e.batch_id = ? and t.role = 'ASSISTANT'", id)).isZero();
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and start_at::date = date '2026-11-20'", id)).isEqualTo(6);
    }

    @Test
    void badInputIsRefusedWithAStableCode() {
        setUp();

        expect(input("REPEAT_UNTIL", null, "2026-11-15", true, true), "NO_TARGET_WEEKS");
        expect(input("SELECTED_WEEKS", List.of("2026-11-10"), null, true, true), "NO_TARGET_WEEKS");
        expect(input("EVERY_DAY", null, null, true, true), "INVALID_MODE");
        assertThatThrownBy(() -> schedule.reuseWeek(tenant, input("SELECTED_WEEKS", List.of("2026-11-10"), null, true, true), admin))
                .isInstanceOfSatisfying(TeachingScheduleException.class, e -> assertThat(e.getCode()).isEqualTo("NO_TARGET_WEEKS"));
        assertThat(count("select count(*) from teaching_schedule_batches where tenant_id = ?", tenant)).isEqualTo(3);
    }

    private void expect(TeachingScheduleReuseInput input, String code) {
        assertThatThrownBy(() -> schedule.previewReuse(tenant, input)).isInstanceOfSatisfying(TeachingScheduleException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
