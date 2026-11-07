package com.erg.ergspringboot.timekeeping.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.timekeeping.PeriodStage;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.DayCell;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.Timesheet;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.TimesheetRow;
import com.erg.ergspringboot.timekeeping.internal.request.RequestServiceIT.Fixture;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TimesheetServiceIT extends AdminITBase {

    private static final String T = "tk-sheet-it";

    @Autowired TimesheetService sheets;

    private Fixture f;

    @BeforeEach
    void seed() {
        f = Fixture.seed(jdbc, T, "sh");
        String t = f.teacher();
        day(T, f.school(), t, nov(2), "FULL", "1.0", 0, null);
        day(T, f.school(), t, nov(3), "LATE_EARLY", "1.0", 10, null);
        day(T, f.school(), t, nov(4), "HALF", "0.5", 0, "ANNUAL");
        day(T, f.school(), t, nov(5), "LEAVE", "0", 0, "UNPAID");
        day(T, f.school(), t, nov(6), "SICK", "0", 0, "SICK");
        day(T, f.school(), t, nov(9), "MISSING_PUNCH", "0.5", 0, null);
        day(T, f.school(), t, nov(10), "BUSINESS", "1.0", 0, null);
        day(T, f.school(), t, nov(20), "HOLIDAY", "0", 0, null);
        day(T, f.school(), t, nov(21), "HOLIDAY", "0", 0, null); // a Saturday: not a paid work day
        jdbc.update("insert into tk_holidays (id, tenant_id, school_id, holiday_date, name) values (?, ?, ?, ?, 'Ngày NGVN')",
                UUID.randomUUID().toString(), T, f.school(), nov(20));
        jdbc.update("insert into tk_holidays (id, tenant_id, school_id, holiday_date, name) values (?, ?, null, ?, 'Lễ')",
                UUID.randomUUID().toString(), T, nov(21));
    }

    @Test
    void mapsDayRowsToTheSheetSymbolsAndTotals() {
        Timesheet sheet = sheets.timesheet(T, f.school(), "2026-11", null, "TÊN RQ_T", 0, 50);

        assertThat(sheet.standardDays()).isEqualTo(20); // 21 weekdays in 11/2026, minus Friday 20/11
        assertThat(sheet.stage()).isEqualTo(PeriodStage.COLLECT);
        assertThat(sheet.reviewUntil()).isEqualTo(LocalDate.of(2026, 12, 8));
        assertThat(sheet.issues().missingDays()).isEqualTo(1);
        assertThat(sheet.total()).isEqualTo(1);
        TimesheetRow row = sheet.rows().get(0);
        assertThat(row.days()).hasSize(30);
        assertThat(row.days().subList(1, 10)).extracting(DayCell::code)
                .containsExactly("X", "M", "½", "KP", "Ô", "", "", "KC", "CT");
        assertThat(row.days().get(19).code()).isEqualTo("L");
        assertThat(row.work()).isEqualByComparingTo("4.0");
        assertThat(row.late()).isEqualTo(1);
        assertThat(row.leave()).isEqualByComparingTo("1.5");
        assertThat(row.kpKc()).isEqualByComparingTo("2.0");
        assertThat(row.deductMinutes()).isEqualTo(15);
    }

    @Test
    void theRosterHoldsSelfHoldersOfTheSchoolPlusAnyoneWithRows() {
        assertThat(sheets.timesheet(T, f.school(), "2026-11", null, null, 0, 50).total()).isEqualTo(3); // 2 VPs + teacher
    }

    private static LocalDate nov(int d) {
        return LocalDate.of(2026, 11, d);
    }
}
