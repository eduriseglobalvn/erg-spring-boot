package com.erg.ergspringboot.timekeeping.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyCommand;
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
