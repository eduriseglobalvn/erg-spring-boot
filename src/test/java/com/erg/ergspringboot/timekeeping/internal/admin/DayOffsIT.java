package com.erg.ergspringboot.timekeeping.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.CalendarHolidayPort;
import com.erg.ergspringboot.calendar.CalendarHolidayPort.HolidayImpact;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleDataLoader;
import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.exception.HolidayAffectsPastPeriodsException;
import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.HolidayKind;
import com.erg.ergspringboot.timekeeping.HolidayPayMode;
import com.erg.ergspringboot.timekeeping.HolidayTeachingAction;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.SaturdayMode;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportPreview;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportRow;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyCommand;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.request.LeaveBalanceService;
import com.erg.ergspringboot.timekeeping.internal.request.RequestServiceIT.Fixture;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Days off and Saturday modes (KE_HOACH_LICH_DAY.md B11) against a real PostgreSQL. The clock is Thu 12/11/2026
 * 08:00 Vietnam, so 10/11 is past and 20/11 is future. Plain JDBC writes the calendar fixtures.
 */
class DayOffsIT extends AdminITBase {

    private static final String T = "tk-dayoff-it";
    private static final LocalDate NGVN = LocalDate.of(2026, 11, 20); // Friday
    private static final LocalDate PAST = LocalDate.of(2026, 11, 10); // Tuesday, before "now"

    @Autowired HolidayService holidays;
    @Autowired HolidayImportService imports;
    @Autowired SaturdayModeService saturdays;
    @Autowired TimekeepingConfigService config;
    @Autowired DayAggregationService aggregation;
    @Autowired LeaveBalanceService balances;
    @Autowired WorkContextResolver context;
    @Autowired ScheduleDataLoader loader;
    @Autowired CalendarHolidayPort calendarPort;

    private Fixture f;
    private String school2;
    private String batch;

    @BeforeEach
    void seed() {
        f = Fixture.seed(jdbc, T, "do");
        school2 = "sch_do2";
        jdbc.update("insert into education_units (id, tenant_id, name, slug, type, status, created_at, updated_at)"
                + " values (?, ?, 'Trường thứ hai', ?, 'school', 'active', now(), now())", school2, T, school2);
        jdbc.update("insert into academic_classes (id, tenant_id, school_id, name, created_at, updated_at)"
                + " values ('cls_do1', ?, ?, '6A1', now(), now()), ('cls_do2', ?, ?, '7A1', now(), now())", T, f.school(), T, school2);
        jdbc.update("insert into curriculum_subjects (id, tenant_id, code, name, created_at, updated_at) values ('sub_do', ?, 'sub_do', 'Tin', now(), now())", T);
        batch = "batch_do";
        jdbc.update("insert into teaching_schedule_batches (id, tenant_id, school_id, apply_from, repeat_weeks, status, label)"
                + " values (?, ?, ?, date '2026-11-02', 10, 'PUBLISHED', 'Đợt 1')", batch, T, f.school());
    }

    private String event(String id, String school, String classId, LocalDate day, int hour, String status, String note) {
        java.time.OffsetDateTime start = day.atTime(hour, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        jdbc.update("insert into teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id,"
                + " start_at, end_at, weekday, period_start, period_count, status, note) values (?, ?, ?, ?, ?, 'sub_do',"
                + " ?, ?, ?, 1, 1, ?, ?)", id, T, batch, school, classId, java.sql.Timestamp.from(start.toInstant()),
                java.sql.Timestamp.from(start.plusMinutes(45).toInstant()), day.getDayOfWeek().getValue(), status, note);
        jdbc.update("insert into teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role)"
                + " values (?, ?, ?, ?, 'MAIN')", UUID.randomUUID().toString(), T, id, f.teacher());
        return id;
    }

    private String status(String eventId) {
        return jdbc.queryForObject("select status from teaching_schedule_events where id = ?", String.class, eventId);
    }

    private String cancelledBy(String eventId) {
        return jdbc.queryForObject("select cancelled_by_holiday_id from teaching_schedule_events where id = ?", String.class, eventId);
    }

    private static HolidayCommand command(LocalDate from, LocalDate to, HolidayKind kind, HolidayPayMode pay,
            HolidayTeachingAction action, Boolean confirmPast) {
        return new HolidayCommand(from, to, "Ngày Nhà giáo Việt Nam", kind, pay, action, confirmPast);
    }

    private static void assertCode(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- test 1, 2: cancel, restore only its own

    @Test
    void aCancellingDayOffCancelsPublishedPeriodsOfEverySchoolAndKeepRestoresOnlyThose() {
        event("ev1", f.school(), "cls_do1", NGVN, 7, "PUBLISHED", null);
        event("ev2", f.school(), "cls_do1", NGVN, 8, "PUBLISHED", null);
        event("ev3", school2, "cls_do2", NGVN, 7, "PUBLISHED", null);
        event("evBatch", f.school(), "cls_do1", NGVN, 9, "CANCELLED", null); // cancelled with its batch earlier
        event("evOther", f.school(), "cls_do1", NGVN.plusDays(7), 7, "PUBLISHED", null);

        HolidayConfig day = holidays.createHoliday(T, f.lead(), command(NGVN, null, HolidayKind.COMPANY,
                HolidayPayMode.UNPAID, HolidayTeachingAction.CANCEL, null)).get(0);
        flush();

        assertThat(List.of(status("ev1"), status("ev2"), status("ev3"))).containsOnly("CANCELLED");
        assertThat(List.of(cancelledBy("ev1"), cancelledBy("ev2"), cancelledBy("ev3"))).containsOnly(day.id());
        assertThat(cancelledBy("evBatch")).isNull();
        assertThat(status("evOther")).isEqualTo("PUBLISHED");
        HolidayConfig listed = holidays.holidays(T, null, 2026).get(0);
        assertThat(listed.affectedPeriods()).isEqualTo(3);
        assertThat(listed.affectedSchools()).isEqualTo(2);
        assertThat(listed.payMode()).isEqualTo(HolidayPayMode.UNPAID);

        holidays.updateHoliday(T, f.lead(), day.id(), command(NGVN, null, HolidayKind.COMPANY, HolidayPayMode.UNPAID,
                HolidayTeachingAction.KEEP, null));
        flush();

        assertThat(List.of(status("ev1"), status("ev2"), status("ev3"))).containsOnly("PUBLISHED");
        assertThat(cancelledBy("ev1")).isNull();
        assertThat(status("evBatch")).isEqualTo("CANCELLED"); // cancelled for another reason: untouched
        assertThat(loader.loadHolidays(T, NGVN, NGVN, List.of(f.school()))).isEmpty(); // KEEP is an ordinary teaching day

        holidays.updateHoliday(T, f.lead(), day.id(), command(NGVN, null, HolidayKind.COMPANY, HolidayPayMode.UNPAID,
                HolidayTeachingAction.CANCEL, null));
        holidays.deleteHoliday(T, f.lead(), day.id());
        flush();
        assertThat(status("ev1")).isEqualTo("PUBLISHED"); // deleting the day off restores its periods too
        assertThat(jdbc.queryForObject("select count(*) from tk_holidays where tenant_id = ?", Integer.class, T)).isZero();
    }

    // ---- test 3: validation

    @Test
    void payModeIsRequiredAndStateAndCompensatoryDaysMustBePaid() {
        assertCode(() -> holidays.createHoliday(T, f.lead(), command(NGVN, null, HolidayKind.COMPANY, null, null, null)),
                HttpStatus.BAD_REQUEST, "HOLIDAY_PAY_MODE_REQUIRED");
        assertCode(() -> holidays.createHoliday(T, f.lead(), command(NGVN, null, HolidayKind.STATE, HolidayPayMode.UNPAID, null, null)),
                HttpStatus.BAD_REQUEST, "HOLIDAY_STATE_MUST_BE_PAID");
        assertCode(() -> holidays.createHoliday(T, f.lead(),
                command(NGVN, null, HolidayKind.COMPENSATORY, HolidayPayMode.ANNUAL_LEAVE, null, null)),
                HttpStatus.BAD_REQUEST, "HOLIDAY_STATE_MUST_BE_PAID");
        List<HolidayConfig> range = holidays.createHoliday(T, f.lead(),
                command(NGVN, NGVN.plusDays(2), HolidayKind.COMPANY, HolidayPayMode.ANNUAL_LEAVE, null, null));
        assertThat(range).extracting(HolidayConfig::date).containsExactly(NGVN, NGVN.plusDays(1), NGVN.plusDays(2));
        assertCode(() -> holidays.createHoliday(T, f.lead(), command(NGVN, null, HolidayKind.COMPANY, HolidayPayMode.PAID, null, null)),
                HttpStatus.CONFLICT, "HOLIDAY_EXISTS");
    }

    // ---- test 4: a legacy school row does not leak to other schools

    @Test
    void anOldSchoolRowOnlyAppliesToThatSchool() {
        jdbc.update("insert into tk_holidays (id, tenant_id, school_id, holiday_date, name, kind, pay_mode, teaching_action)"
                + " values ('old1', ?, ?, ?, 'Lễ riêng', 'COMPANY', 'PAID', 'CANCEL')", T, f.school(), NGVN);
        jdbc.update("insert into tk_holidays (id, tenant_id, school_id, holiday_date, name, kind, pay_mode, teaching_action)"
                + " values ('keep1', ?, null, ?, 'Vẫn dạy', 'COMPANY', 'PAID', 'KEEP')", T, NGVN.plusDays(1));

        assertThat(loader.loadHolidays(T, NGVN, NGVN.plusDays(1), List.of(f.school()))).containsOnlyKeys(NGVN);
        assertThat(loader.loadHolidays(T, NGVN, NGVN.plusDays(1), List.of(school2))).isEmpty();
        assertThat(loader.loadHolidays(T, NGVN, NGVN.plusDays(1), List.of(f.school(), school2))).containsOnlyKeys(NGVN);

        event("evA", f.school(), "cls_do1", NGVN, 7, "PUBLISHED", null);
        event("evB", school2, "cls_do2", NGVN, 7, "PUBLISHED", null);
        // the row predates this feature, so its periods were never cancelled: switching KEEP -> CANCEL applies it
        holidays.updateHoliday(T, f.lead(), "old1", command(NGVN, null, HolidayKind.COMPANY, HolidayPayMode.PAID,
                HolidayTeachingAction.KEEP, null));
        holidays.updateHoliday(T, f.lead(), "old1", command(NGVN, null, HolidayKind.COMPANY, HolidayPayMode.PAID,
                HolidayTeachingAction.CANCEL, null));
        flush();
        assertThat(status("evA")).isEqualTo("CANCELLED");
        assertThat(status("evB")).isEqualTo("PUBLISHED"); // the other school's period is not touched
    }

    // ---- test 5: periods already taught

    @Test
    void cancellingTaughtPeriodsNeedsConfirmationAndStillStopsAtALockedPeriod() {
        event("p1", f.school(), "cls_do1", PAST, 7, "PUBLISHED", "REPORTED");
        event("p2", f.school(), "cls_do1", PAST, 8, "PUBLISHED", null);
        event("p3", school2, "cls_do2", PAST, 7, "PUBLISHED", "REPORTED");
        event("evening", f.school(), "cls_do1", PAST, 17, "PUBLISHED", null);
        HolidayCommand cmd = command(PAST, null, HolidayKind.COMPANY, HolidayPayMode.PAID, HolidayTeachingAction.CANCEL, null);

        assertThatThrownBy(() -> holidays.createHoliday(T, f.lead(), cmd)).isInstanceOfSatisfying(
                HolidayAffectsPastPeriodsException.class, e -> {
                    HolidayImpact impact = (HolidayImpact) e.payload();
                    assertThat(impact.dates()).containsExactly(PAST);
                    assertThat(impact.taughtPeriods()).isEqualTo(4);
                    assertThat(impact.reportedPeriods()).isEqualTo(2);
                    assertThat(impact.schools()).hasSize(2);
                    assertThat(impact.schools()).extracting(s -> s.taughtPeriods() + "/" + s.reportedPeriods())
                            .containsExactlyInAnyOrder("3/1", "1/1");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("HOLIDAY_AFFECTS_PAST_PERIODS");
                });
        flush();
        assertThat(jdbc.queryForObject("select count(*) from tk_holidays where tenant_id = ?", Integer.class, T)).isZero();
        assertThat(status("p1")).isEqualTo("PUBLISHED");

        jdbc.update("insert into tk_period_locks (id, tenant_id, school_id, period, locked_at, locked_by)"
                + " values ('lock-do', ?, ?, '2026-11', now(), 'admin')", T, school2);
        assertCode(() -> holidays.createHoliday(T, f.lead(), command(PAST, null, HolidayKind.COMPANY, HolidayPayMode.PAID,
                HolidayTeachingAction.CANCEL, true)), HttpStatus.CONFLICT, "PERIOD_LOCKED");
        jdbc.update("delete from tk_period_locks where id = 'lock-do'");

        holidays.createHoliday(T, f.lead(), command(PAST, null, HolidayKind.COMPANY, HolidayPayMode.PAID,
                HolidayTeachingAction.CANCEL, true));
        flush();
        assertThat(List.of(status("p1"), status("p2"), status("p3"), status("evening"))).containsOnly("CANCELLED");
    }

    // ---- test 6, 9: timekeeping

    @Test
    void aPaidDayOffIsOneCongAndStaysInStandardDaysAndAPunchOnItIsWorked() {
        holidays.createHoliday(T, f.lead(), command(NGVN, null, HolidayKind.STATE, HolidayPayMode.PAID, null, null));
        holidays.createHoliday(T, f.lead(), command(LocalDate.of(2026, 11, 21), null, HolidayKind.STATE, HolidayPayMode.PAID, null, null));
        holidays.createHoliday(T, f.lead(), command(PAST, null, HolidayKind.STATE, HolidayPayMode.PAID, null, null));
        punch(T, f.school(), f.teacher(), "IN", java.time.Instant.parse("2026-11-10T00:25:00Z"), "VALID");
        punch(T, f.school(), f.teacher(), "OUT", java.time.Instant.parse("2026-11-10T04:35:00Z"), "VALID");

        aggregation.recompute(T, f.teacher(), NGVN);
        aggregation.recompute(T, f.teacher(), LocalDate.of(2026, 11, 21)); // Saturday: no shift that day
        aggregation.recompute(T, f.teacher(), PAST);
        flush();

        assertThat(dayRow(NGVN)).containsEntry("status", "HOLIDAY");
        assertThat(dayRow(NGVN).get("work_units").toString()).isEqualTo("1.0");
        assertThat(dayRow(LocalDate.of(2026, 11, 21)).get("work_units").toString()).isEqualTo("0.0"); // not paid twice
        assertThat(dayRow(PAST)).containsEntry("status", "FULL"); // punched: worked like a working day
    }

    @Test
    void annualLeaveDayOffIsPAndCostsADayThenKlWhenTheBalanceIsGone() {
        config.savePolicy(T, f.lead(), f.school(), new PolicyCommand(null, null, null, null, null, null, 0, 20, null, null));
        jdbc.update("update employee_profiles set joined_at = '2026-06-01' where user_id = ?", f.teacher());
        // entitlement: 0 base days and no full seniority step yet = 0 days, so the day cannot be covered
        holidays.createHoliday(T, f.lead(), command(NGVN, null, HolidayKind.COMPANY, HolidayPayMode.ANNUAL_LEAVE, null, null));
        aggregation.recompute(T, f.teacher(), NGVN);
        flush();
        assertThat(dayRow(NGVN)).containsEntry("status", "UNPAID_OFF");

        config.savePolicy(T, f.lead(), f.school(), new PolicyCommand(null, null, null, null, null, null, 12, 20, null, null));
        flush();
        assertThat(balances.annualBalance(T, f.teacher(), 2026)).isEqualByComparingTo("11"); // 12 - this day
        aggregation.recompute(T, f.teacher(), NGVN);
        flush();
        assertThat(dayRow(NGVN)).containsEntry("status", "LEAVE").containsEntry("leave_kind", LeaveKind.ANNUAL.name());
        assertThat(dayRow(NGVN).get("work_units").toString()).isEqualTo("0.0");
    }

    private Map<String, Object> dayRow(LocalDate date) {
        return jdbc.queryForMap("select status, work_units, leave_kind from tk_days where tenant_id = ? and user_id = ?"
                + " and work_date = ?", T, f.teacher(), date);
    }

    // ---- test 7, 8: Saturday modes

    @Test
    void saturdayModesDriveTheDayAndWinOverTheWeeklyRuleAndDoNotMoveSchoolPeriods() {
        jdbc.update("update tk_shifts set weekdays = '{1,2,3,4,5,6}' where school_id = ?", f.school());
        LocalDate wfh = LocalDate.of(2026, 11, 14);
        LocalDate paidOff = LocalDate.of(2026, 11, 21);
        LocalDate unpaidOff = LocalDate.of(2026, 11, 28);
        LocalDate plain = LocalDate.of(2026, 11, 7);
        event("sat", f.school(), "cls_do1", paidOff, 7, "PUBLISHED", null);

        saturdays.setSaturdayMode(T, f.lead(), wfh, SaturdayMode.WFH);
        saturdays.setSaturdayMode(T, f.lead(), paidOff, SaturdayMode.PAID_OFF);
        saturdays.setSaturdayMode(T, f.lead(), unpaidOff, SaturdayMode.UNPAID_OFF);
        for (LocalDate d : List.of(wfh, paidOff, unpaidOff, plain)) aggregation.recompute(T, f.teacher(), d);
        flush();

        assertThat(dayRow(paidOff)).containsEntry("status", "HOLIDAY");
        assertThat(dayRow(paidOff).get("work_units").toString()).isEqualTo("1.0");
        assertThat(dayRow(unpaidOff)).containsEntry("status", "UNPAID_OFF");
        assertThat(dayRow(plain)).containsEntry("status", "ABSENT_UNPAID"); // no row: an ordinary working Saturday
        assertThat(status("sat")).isEqualTo("PUBLISHED"); // school periods do not follow the Saturday mode
        String shiftId = "shift_" + f.school();
        assertThat(context.isRemote(T, f.school(), f.teacher(), wfh, shiftId)).isTrue();
        assertThat(context.isRemote(T, f.school(), f.teacher(), plain, shiftId)).isFalse();

        assertThat(saturdays.saturdayModes(T, "2026-11")).extracting(v -> v.date() + "=" + v.mode()).containsExactly(
                "2026-11-07=OFFICE", "2026-11-14=WFH", "2026-11-21=PAID_OFF", "2026-11-28=UNPAID_OFF");
        saturdays.setSaturdayMode(T, f.lead(), wfh, SaturdayMode.OFFICE); // choosing "Đi làm" removes the row
        assertThat(jdbc.queryForObject("select count(*) from tk_saturday_modes where tenant_id = ? and work_date = ?",
                Integer.class, T, wfh)).isZero();
        assertCode(() -> saturdays.setSaturdayMode(T, f.lead(), LocalDate.of(2026, 11, 20), SaturdayMode.WFH),
                HttpStatus.BAD_REQUEST, "SATURDAY_MODE_NOT_SATURDAY");
    }

    @Test
    void aSaturdayOffDoesNotCountAsALeaveDay() {
        jdbc.update("update tk_shifts set weekdays = '{1,2,3,4,5,6}' where school_id = ?", f.school());
        LocalDate sat = LocalDate.of(2026, 11, 21);
        assertThat(balances.countDays(T, f.school(), sat, sat, com.erg.ergspringboot.timekeeping.DayPart.FULL))
                .isEqualByComparingTo("1");
        saturdays.setSaturdayMode(T, f.lead(), sat, SaturdayMode.UNPAID_OFF);
        assertThat(balances.countDays(T, f.school(), sat, sat, com.erg.ergspringboot.timekeeping.DayPart.FULL))
                .isEqualByComparingTo("0");
    }

    // ---- test 10: import

    @Test
    void importPreviewMarksExistingAndVerifyDaysAndImportSkipsWhatExists() throws Exception {
        byte[] file = Files.readAllBytes(Path.of("src/test/resources/timekeeping/holidays/sample-vn-2026.ics"));
        holidays.createHoliday(T, f.lead(), new HolidayCommand(LocalDate.of(2026, 1, 1), null, "Tết Dương lịch",
                HolidayKind.STATE, HolidayPayMode.PAID, HolidayTeachingAction.KEEP, null));

        HolidayImportPreview preview = imports.preview(T, 2026, file);

        assertThat(preview.source()).isEqualTo("UPLOAD");
        assertThat(preview.rows()).extracting(r -> r.date() + ":" + r.status()).containsExactly("2026-01-01:EXISTS",
                "2026-04-26:NEW", "2026-05-04:NEW", "2026-09-02:NEW", "2026-09-03:NEW", "2026-11-24:NEW");
        assertThat(preview.rows().get(1).lunarDate()).isEqualTo("10/3");
        assertThat(imports.preview(T, 2030, null).source()).isEqualTo("NONE"); // no saved copy for the year, no network
        assertCode(() -> imports.preview(T, 2026, "không phải lịch".getBytes()), HttpStatus.BAD_REQUEST,
                "HOLIDAY_IMPORT_INVALID_FILE");

        var result = imports.importHolidays(T, f.lead(), new HolidayImportCommand(List.of(
                new HolidayImportRow(LocalDate.of(2026, 1, 1), "Tết Dương lịch", HolidayKind.STATE),
                new HolidayImportRow(LocalDate.of(2026, 4, 26), "Giỗ Tổ Hùng Vương", HolidayKind.STATE),
                new HolidayImportRow(LocalDate.of(2026, 5, 4), "Ngày nghỉ bù Ngày Quốc tế Lao động", HolidayKind.COMPENSATORY)), null));
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        flush();
        assertThat(jdbc.queryForList("select pay_mode || '/' || teaching_action || '/' || kind from tk_holidays"
                + " where tenant_id = ? and holiday_date > date '2026-01-01' order by holiday_date", String.class, T))
                .containsExactly("PAID/CANCEL/STATE", "PAID/CANCEL/COMPENSATORY");
    }

    // ---- day loads for the month calendar

    @Test
    void dayLoadsCountPublishedPeriodsAndSchoolsPerDay() {
        event("l1", f.school(), "cls_do1", NGVN, 7, "PUBLISHED", null);
        event("l2", f.school(), "cls_do1", NGVN, 8, "PUBLISHED", null);
        event("l3", school2, "cls_do2", NGVN, 7, "PUBLISHED", null);
        event("l4", f.school(), "cls_do1", NGVN, 9, "DRAFT", null);
        event("l5", f.school(), "cls_do1", NGVN.plusDays(1), 7, "PUBLISHED", null);

        assertThat(calendarPort.dayLoads(T, NGVN.minusDays(1), NGVN.plusDays(1), null))
                .extracting(d -> d.date() + ":" + d.periods() + "/" + d.schools())
                .containsExactly("2026-11-20:3/2", "2026-11-21:1/1");
        assertThat(calendarPort.dayLoads(T, NGVN, NGVN, List.of(school2))).extracting(d -> d.periods()).containsExactly(1);
        assertThat(calendarPort.dayLoads(T, NGVN, NGVN, List.of())).isEmpty();
    }
}
