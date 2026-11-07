package com.erg.ergspringboot.timekeeping.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.ReviewDecision;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PunchReviewCommand;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ShiftCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.SiteCommand;
import com.erg.ergspringboot.timekeeping.internal.request.RequestServiceIT.Fixture;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class TimekeepingConfigServiceIT extends AdminITBase {

    private static final String T = "tk-config-it";

    @Autowired TimekeepingConfigService config;
    @Autowired SiteShiftConfigService sitesShifts;
    @Autowired PunchReviewService reviews;
    @Autowired DayAggregationService aggregation;
    @Autowired WorkContextResolver context;

    private Fixture f;

    @BeforeEach
    void seed() {
        f = Fixture.seed(jdbc, T, "cf");
    }

    @Test
    void policyChangesTakeEffectFromTheNextPeriodAndReplaceTheScheduledOne() {
        var first = config.savePolicy(T, "admin", f.school(), policy(10, null));
        var second = config.savePolicy(T, "admin", f.school(), policy(7, Map.of("LEAVE",
                List.of(Map.of("minDays", 0, "steps", List.of("MANAGER", "SCHOOL_ROLE:VICE_PRINCIPAL"))))));

        assertThat(second.effectiveFrom()).isEqualTo(LocalDate.of(2026, 12, 1)); // today is 12/11/2026
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.lateToleranceMin()).isEqualTo(7);
        var versions = config.policy(T, f.school());
        assertThat(versions.current().lateToleranceMin()).isEqualTo(5); // defaults still in force
        assertThat(versions.next().lateToleranceMin()).isEqualTo(7);
        flush();
        assertThat(jdbc.queryForObject("select count(*) from tk_audit_log where tenant_id = ? and action = 'UPDATE_POLICY'",
                Integer.class, T)).isEqualTo(2);
        assertBad(() -> config.savePolicy(T, "admin", f.school(), policy(61, null)));
        assertBad(() -> config.savePolicy(T, "admin", f.school(), policy(5, Map.of("LEAVE", List.of(Map.of(
                "steps", List.of("PRINCIPAL")))))));
    }

    @Test
    void aRadiusOf40IsABadRequest() {
        assertBad(() -> sitesShifts.saveSite(T, "admin", null, site(40)));
        assertThat(sitesShifts.saveSite(T, "admin", null, site(50)).radiusM()).isEqualTo(50);
    }

    @Test
    void shiftsMustNotOverlapOrCrossMidnightAndAUsedShiftIsOnlyDeactivated() {
        // the fixture's shift runs Mon–Fri
        assertThatThrownBy(() -> sitesShifts.saveShift(T, "admin", null, shift(LocalTime.of(13, 0), LocalTime.of(17, 0), 5)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertBad(() -> sitesShifts.saveShift(T, "admin", null, shift(LocalTime.of(18, 0), LocalTime.of(23, 30), 6)));
        assertBad(() -> sitesShifts.saveShift(T, "admin", null, shift(LocalTime.of(1, 0), LocalTime.of(5, 0), 6)));
        var saturday = sitesShifts.saveShift(T, "admin", null, shift(LocalTime.of(7, 30), LocalTime.of(11, 30), 6));

        flush();
        punch(T, f.school(), f.teacher(), "IN", Instant.parse("2026-11-14T00:30:00Z"), "VALID");
        jdbc.update("update tk_punches set shift_id = ? where tenant_id = ?", saturday.id(), T);
        sitesShifts.deleteShift(T, "admin", saturday.id());
        flush();

        assertThat(jdbc.queryForObject("select active from tk_shifts where id = ?", Boolean.class, saturday.id())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from tk_audit_log where tenant_id = ? and action = 'DEACTIVATE_SHIFT'",
                Integer.class, T)).isEqualTo(1);
    }

    @Test
    void aDeactivatedShiftStillRebuildsItsPastDaysButAppliesToNoLaterDay() {
        LocalDate mon = LocalDate.of(2026, 11, 9);
        String shiftId = "shift_" + f.school();
        String punchId = punch(T, f.school(), f.teacher(), "IN", Instant.parse("2026-11-09T00:25:00Z"), "FLAGGED");
        jdbc.update("update tk_punches set shift_id = ? where id = ?", shiftId, punchId);
        aggregation.recompute(T, f.teacher(), mon);
        flush();
        Map<String, Object> before = dayRow(mon);
        assertThat(before.get("status")).isNotEqualTo("OFF");

        sitesShifts.deleteShift(T, "admin", shiftId); // used by the punch: deactivated on 12/11, not deleted
        var accepted = reviews.review(T, "admin", punchId, new PunchReviewCommand(ReviewDecision.ACCEPT, null));
        flush();

        assertThat(accepted.status()).isEqualTo(PunchStatus.VALID);
        assertThat(dayRow(mon)).isEqualTo(before); // the rebuilt past day keeps its shift
        assertThat(context.shiftFor(T, f.school(), LocalDate.of(2026, 11, 11))).isPresent();
        assertThat(context.shiftFor(T, f.school(), LocalDate.of(2026, 11, 12))).isEmpty();
        assertThat(context.shiftFor(T, f.school(), LocalDate.of(2026, 11, 13))).isEmpty();
        // a reviewed punch cannot be reviewed again
        assertConflict(() -> reviews.review(T, "admin", punchId, new PunchReviewCommand(ReviewDecision.ACCEPT, null)));
    }

    @Test
    void editingTheHoursOfAShiftUsedByDaysRetiresItAndCreatesANewRowFromTodayKeepingItsRemoteRules() {
        String shiftId = "shift_" + f.school();
        LocalDate mon = LocalDate.of(2026, 11, 9);
        day(T, f.school(), f.teacher(), mon, "FULL", "1.0", 0, null);
        jdbc.update("update tk_days set shift_id = ? where tenant_id = ?", shiftId, T);
        jdbc.update("insert into tk_remote_rules (id, tenant_id, school_id, kind, weekday, shift_id)"
                + " values ('rr-cf', ?, ?, 'WEEKDAY', 5, ?)", T, f.school(), shiftId); // Fridays remote

        var edited = sitesShifts.saveShift(T, "admin", shiftId, new ShiftCommand(f.school(), "Ca sáng",
                LocalTime.of(8, 0), LocalTime.of(11, 30), BigDecimal.ONE, List.of(1, 2, 3, 4, 5), true));
        flush();

        assertThat(edited.id()).isNotEqualTo(shiftId);
        assertThat(context.shiftFor(T, f.school(), mon)).get().extracting(s -> s.getStartTime())
                .isEqualTo(LocalTime.of(7, 30)); // past days keep the old hours
        assertThat(context.shiftFor(T, f.school(), LocalDate.of(2026, 11, 13))).get().extracting(s -> s.getId())
                .isEqualTo(edited.id());
        // the shift-scoped remote rule follows the new row
        assertThat(context.isRemote(T, f.school(), f.teacher(), LocalDate.of(2026, 11, 13), edited.id())).isTrue();
        // the new row is not used by any day yet, so editing it again stays in place
        assertThat(sitesShifts.saveShift(T, "admin", edited.id(), new ShiftCommand(f.school(), "Ca chính",
                LocalTime.of(8, 0), LocalTime.of(11, 30), BigDecimal.ONE, List.of(1, 2, 3, 4, 5), true)).id())
                .isEqualTo(edited.id());
    }

    @Test
    void holidaysRespectLocksOfEverySchoolAndRebuildStoredDays() {
        jdbc.update("insert into tk_period_locks (id, tenant_id, school_id, period, locked_at, locked_by)"
                + " values ('lock-cf', ?, ?, '2026-10', now(), 'admin')", T, f.school());
        LocalDate oct5 = LocalDate.of(2026, 10, 5);
        assertConflict(() -> config.createHoliday(T, "admin", new HolidayCommand(f.school(), oct5, "Lễ")));
        assertConflict(() -> config.createHoliday(T, "admin", new HolidayCommand(null, oct5, "Lễ"))); // tenant-wide

        LocalDate tue = LocalDate.of(2026, 11, 10);
        day(T, f.school(), f.teacher(), tue, "ABSENT_UNPAID", "0", 0, null);
        config.createHoliday(T, "admin", new HolidayCommand(null, tue, "Nghỉ bù"));
        flush();
        assertThat(dayRow(tue).get("status")).isEqualTo("HOLIDAY");
    }

    private Map<String, Object> dayRow(LocalDate date) {
        return jdbc.queryForMap("select status, work_units from tk_days where tenant_id = ? and user_id = ?"
                + " and work_date = ?", T, f.teacher(), date);
    }

    private static void assertConflict(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private SiteCommand site(int radius) {
        return new SiteCommand(f.school(), "Cổng chính", null, new BigDecimal("21.028500"), new BigDecimal("105.854200"),
                radius, true);
    }

    private ShiftCommand shift(LocalTime start, LocalTime end, int weekday) {
        return new ShiftCommand(f.school(), "Ca", start, end, BigDecimal.ONE, List.of(weekday), true);
    }

    private static PolicyCommand policy(int tolerance, Map<String, Object> chains) {
        return new PolicyCommand(tolerance, null, null, null, null, null, null, null, null, chains);
    }

    private static void assertBad(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
