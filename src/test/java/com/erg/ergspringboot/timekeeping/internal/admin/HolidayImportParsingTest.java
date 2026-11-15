package com.erg.ergspringboot.timekeeping.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.timekeeping.HolidayKind;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The .ics filter rules of B11 and the lunar dates shown next to them. Pure: no database, no network. */
class HolidayImportParsingTest {

    private static String sample() throws IOException {
        return Files.readString(Path.of("src/test/resources/timekeeping/holidays/sample-vn-2026.ics"), StandardCharsets.UTF_8);
    }

    @Test
    void keepsPublicHolidaysDropsCommemorationsAndSplitsMultiDayEvents() throws IOException {
        List<HolidayImportRow> rows = HolidayImportService.days(sample(), 2026);

        assertThat(rows).extracting(HolidayImportRow::date).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 26), LocalDate.of(2026, 5, 4), LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3), LocalDate.of(2026, 11, 24)); // no 25/12 (Giáng sinh) and nothing of 2027
        assertThat(rows.get(0).name()).isEqualTo("Tết Dương lịch"); // the description's footer line is ignored
        assertThat(rows.get(3).name()).isEqualTo("Quốc khánh, Ngày Độc lập"); // escaped comma undone
        assertThat(rows.get(2).name()).isEqualTo("Ngày nghỉ bù Ngày Quốc tế Lao" + "động"); // folded line joined
        assertThat(rows).extracting(HolidayImportRow::kind).containsExactly(HolidayKind.STATE, HolidayKind.STATE,
                HolidayKind.COMPENSATORY, HolidayKind.STATE, HolidayKind.STATE, HolidayKind.STATE);
    }

    @Test
    void aFileThatIsNotICalendarIsRejectedWithItsOwnCode() {
        assertThatThrownBy(() -> HolidayImportService.days("tên,ngày\nLễ,2026-01-01", 2026))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("HOLIDAY_IMPORT_INVALID_FILE"));
    }

    @Test
    void lunarDatesMatchTheOfficialCalendar() {
        // Tết (1/1): 29/01/2025, 17/02/2026, 06/02/2027. Giỗ Tổ Hùng Vương (10/3). Tết Trung thu (15/8).
        assertThat(VietnameseLunarCalendar.format(LocalDate.of(2025, 1, 29))).isEqualTo("1/1");
        assertThat(VietnameseLunarCalendar.format(LocalDate.of(2026, 2, 17))).isEqualTo("1/1");
        assertThat(VietnameseLunarCalendar.format(LocalDate.of(2027, 2, 6))).isEqualTo("1/1");
        assertThat(VietnameseLunarCalendar.format(LocalDate.of(2025, 4, 7))).isEqualTo("10/3");
        assertThat(VietnameseLunarCalendar.format(LocalDate.of(2026, 4, 26))).isEqualTo("10/3");
        assertThat(VietnameseLunarCalendar.format(LocalDate.of(2025, 10, 6))).isEqualTo("15/8");
        assertThat(VietnameseLunarCalendar.format(LocalDate.of(2026, 9, 25))).isEqualTo("15/8");
    }
}
