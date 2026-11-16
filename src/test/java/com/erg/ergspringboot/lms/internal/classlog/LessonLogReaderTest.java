package com.erg.ergspringboot.lms.internal.classlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogLag;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LessonLogReaderTest {

    private static LessonLogPeriod period(String classId, int ppct, int actual, boolean happened, String status) {
        return new LessonLogPeriod("e", LocalDate.of(2026, 11, 9), 1, classId, classId, "s1", "Tin", null, ppct, actual, null,
                List.of(), null, status, false, happened, 0, null);
    }

    @Test
    void theBoardClassGivesALagOfTwoAndIgnoresOnTimeClasses() {
        // 6A6: PPCT 10/11 but taught 8/9; 6A7 is on time; 6A8 is one behind
        List<LessonLogPeriod> week = List.of(
                period("6A6", 10, 8, true, "PENDING"), period("6A6", 11, 9, true, "PENDING"),
                period("6A7", 4, 4, true, "PENDING"), period("6A8", 7, 6, true, "PENDING"));

        LessonLogLag chip = LessonLogReader.lagChip(week);

        assertThat(chip).isEqualTo(new LessonLogLag("6A6", "6A6", 2));
    }

    @Test
    void noLagMeansNoChipAndTaughtAheadIsNotALag() {
        assertThat(LessonLogReader.lagChip(List.of(period("6A6", 5, 5, true, "PENDING"), period("6A6", 5, 7, true, "PENDING"))))
                .isNull();
    }

    @Test
    void statsCountOnlyHappenedPeriodsAsPending() {
        var stats = LessonLogReader.stats(List.of(period("a", 1, 1, true, "CONFIRMED"), period("a", 2, 2, true, "PENDING"),
                period("a", 3, 3, false, "PENDING")));

        assertThat(stats.taught()).isEqualTo(2);
        assertThat(stats.confirmed()).isEqualTo(1);
        assertThat(stats.pending()).isEqualTo(1);
    }

    @Test
    void thePeriodsOfAMultiPeriodEventEndOneAfterTheOther() {
        // periods 4-5 from 13:00 to 14:30 VN (06:00Z-07:30Z)
        LessonSlot slot = new LessonSlot("e", "c", "s", LocalDate.of(2026, 11, 9), 4, 2,
                Instant.parse("2026-11-09T06:00:00Z"), Instant.parse("2026-11-09T07:30:00Z"), "sch", null, null, List.of());
        Instant between = Instant.parse("2026-11-09T06:50:00Z");

        assertThat(LessonLogReader.happened(slot, 4, between)).isTrue();
        assertThat(LessonLogReader.happened(slot, 5, between)).isFalse();
        assertThat(LessonLogReader.happened(slot, 5, Instant.parse("2026-11-09T07:30:00Z"))).isTrue();
    }

    @Test
    void theShortNameIsTheLastWordOfTheFullName() {
        assertThat(LessonLogReader.shortName("  Nguyễn Văn Minh ")).isEqualTo("Minh");
        assertThat(LessonLogReader.shortName(null)).isEmpty();
    }

    @Test
    void curriculumYearsStartInSeptember() {
        assertThat(CurriculumLookup.academicYear(LocalDate.of(2026, 11, 9))).isEqualTo("2026-2027");
        assertThat(CurriculumLookup.academicYear(LocalDate.of(2027, 5, 1))).isEqualTo("2026-2027");
        assertThat(CurriculumLookup.academicYear(LocalDate.of(2026, 8, 31))).isEqualTo("2025-2026");
    }
}
