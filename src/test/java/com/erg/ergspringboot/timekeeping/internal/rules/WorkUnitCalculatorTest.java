package com.erg.ergspringboot.timekeeping.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.Missing;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Shift 07:30-11:30, 1 unit, tolerance 5, block 15. */
class WorkUnitCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private static Instant at(String hhmm) {
        return hhmm == null ? null : DAY.atTime(LocalTime.parse(hhmm)).atZone(ZONE).toInstant();
    }

    private static DayResult run(String in, String out, boolean closed) {
        return WorkUnitCalculator.compute(new DayInput(
                LocalTime.of(7, 30), LocalTime.of(11, 30), BigDecimal.ONE, at(in), at(out), 5, 15, ZONE, closed));
    }

    private static void units(DayResult r, String expected) {
        assertThat(r.workUnits()).isEqualByComparingTo(expected);
    }

    @Test
    void onTimeIsFull() {
        DayResult r = run("07:24", "11:35", true);
        assertThat(r.status()).isEqualTo(DayStatus.FULL);
        units(r, "1");
        assertThat(r.lateMinutes()).isZero();
        assertThat(r.earlyMinutes()).isZero();
        assertThat(r.deductMinutes()).isZero();
        assertThat(r.missing()).isNull();
    }

    @Test
    void zoneIsHonoured() {
        // 00:37 UTC is 07:37 in Ho Chi Minh, whatever the JVM default zone is.
        Instant in = Instant.parse("2026-11-12T00:37:00Z");
        DayResult r = WorkUnitCalculator.compute(new DayInput(
                LocalTime.of(7, 30), LocalTime.of(11, 30), BigDecimal.ONE, in, at("11:35"), 5, 15, ZONE, true));
        assertThat(r.lateMinutes()).isEqualTo(7);
    }

    @Test
    void lateKeepsFullUnitButDeductsOneBlock() {
        DayResult r = run("07:37", "11:35", true);
        assertThat(r.status()).isEqualTo(DayStatus.LATE_EARLY);
        units(r, "1");
        assertThat(r.lateMinutes()).isEqualTo(7);
        assertThat(r.deductMinutes()).isEqualTo(15);
    }

    @Test
    void toleranceBoundary() {
        DayResult atTolerance = run("07:35", "11:35", true);
        assertThat(atTolerance.status()).isEqualTo(DayStatus.FULL);
        assertThat(atTolerance.lateMinutes()).isZero();
        assertThat(atTolerance.deductMinutes()).isZero();

        DayResult over = run("07:36", "11:35", true);
        assertThat(over.status()).isEqualTo(DayStatus.LATE_EARLY);
        assertThat(over.lateMinutes()).isEqualTo(6);
        assertThat(over.deductMinutes()).isEqualTo(15);
    }

    @Test
    void deductionRoundsUpToBlocks() {
        assertThat(run("07:46", "11:35", true).deductMinutes()).isEqualTo(30); // 16 late
        assertThat(run("07:45", "11:35", true).deductMinutes()).isEqualTo(15); // 15 late
    }

    @Test
    void earlyLeave() {
        DayResult r = run("07:24", "11:10", true);
        assertThat(r.status()).isEqualTo(DayStatus.LATE_EARLY);
        assertThat(r.earlyMinutes()).isEqualTo(20);
        assertThat(r.lateMinutes()).isZero();
        assertThat(r.deductMinutes()).isEqualTo(30);
        units(r, "1");
    }

    @Test
    void lateAndEarlyAddUpDeductions() {
        DayResult r = run("07:37", "11:10", true);
        assertThat(r.status()).isEqualTo(DayStatus.LATE_EARLY);
        assertThat(r.deductMinutes()).isEqualTo(45);
    }

    @Test
    void closedDayWithoutOutIsMissingOut() {
        DayResult r = run("07:21", null, true);
        assertThat(r.status()).isEqualTo(DayStatus.MISSING_PUNCH);
        units(r, "0.5");
        assertThat(r.missing()).isEqualTo(Missing.OUT);
    }

    @Test
    void closedDayWithoutAnyPunchIsMissingIn() {
        DayResult r = run(null, null, true);
        assertThat(r.status()).isEqualTo(DayStatus.MISSING_PUNCH);
        units(r, "0");
        assertThat(r.missing()).isEqualTo(Missing.IN);
    }

    @Test
    void closedDayWithOutOnlyIsMissingIn() {
        DayResult r = run(null, "11:35", true);
        assertThat(r.status()).isEqualTo(DayStatus.MISSING_PUNCH);
        units(r, "0.5");
        assertThat(r.missing()).isEqualTo(Missing.IN);
    }

    @Test
    void openDayWithInOnlyIsNotMissing() {
        DayResult r = run("07:25", null, false);
        assertThat(r.status()).isEqualTo(DayStatus.FULL);
        assertThat(r.missing()).isNull();
        units(r, "1");
        assertThat(r.earlyMinutes()).isZero();
    }

    @Test
    void openDayWithLateInShowsLateness() {
        DayResult r = run("07:37", null, false);
        assertThat(r.status()).isEqualTo(DayStatus.LATE_EARLY);
        assertThat(r.lateMinutes()).isEqualTo(7);
        assertThat(r.missing()).isNull();
        assertThat(r.deductMinutes()).isEqualTo(15);
    }
}
