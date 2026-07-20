package com.erg.ergspringboot.service.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarBulkInputNormalizerTest {

    private final CalendarBulkInputNormalizer normalizer = new CalendarBulkInputNormalizer();

    @Test
    void trimsAndDeduplicatesIdentifiersOnce() {
        CalendarBulkRowRequest row = row(
                List.of(" class-a ", "class-a", " "),
                List.of(" teacher-a ", "teacher-a"),
                List.of(" teacher-b ", "teacher-b"));
        CalendarBulkCreateRequest normalized = normalizer.normalize(request(List.of(row), 1, " delete_conflicting "));

        assertThat(normalized.schoolId()).isEqualTo("school-a");
        assertThat(normalized.resolveMode()).isEqualTo("DELETE_CONFLICTING");
        assertThat(normalized.rows().getFirst().classIds()).containsExactly("class-a");
        assertThat(normalized.rows().getFirst().mainTeacherIds()).containsExactly("teacher-a");
        assertThat(normalized.rows().getFirst().assistantTeacherIds()).containsExactly("teacher-b");
    }

    @Test
    void rejectsEmptyAndOversizedBulkRequests() {
        assertThatThrownBy(() -> normalizer.normalize(request(List.of(), 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows");

        List<CalendarBulkRowRequest> rows = new ArrayList<>();
        for (int index = 0; index < 201; index++) rows.add(row(List.of("class-a"), List.of("teacher-a"), List.of()));
        assertThatThrownBy(() -> normalizer.normalize(request(rows, 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    @Test
    void rejectsRepeatWeeksOutsideSupportedRange() {
        assertThatThrownBy(() -> normalizer.normalize(request(List.of(row(List.of("class-a"), List.of("teacher-a"), List.of())), 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizer.normalize(request(List.of(row(List.of("class-a"), List.of("teacher-a"), List.of())), 53, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidDateAndTooManyClassesPerRow() {
        CalendarBulkCreateRequest invalidDate = new CalendarBulkCreateRequest(
                "tenant-a", "key-a", "school-a", "2026-2027", "14/07/2026",
                1, null, false, null, List.of(row(List.of("class-a"), List.of("teacher-a"), List.of())));
        assertThatThrownBy(() -> normalizer.normalize(invalidDate)).isInstanceOf(IllegalArgumentException.class);

        List<String> classes = new ArrayList<>();
        for (int index = 0; index < 21; index++) classes.add("class-" + index);
        assertThatThrownBy(() -> normalizer.normalize(request(
                List.of(row(classes, List.of("teacher-a"), List.of())), 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20");
    }

    @Test
    void rejectsOverlongNotesAndInvalidTimes() {
        CalendarBulkRowRequest invalidTime = new CalendarBulkRowRequest(
                "row-a", List.of("class-a"), 2, "MORNING", 1, 2,
                "8am", "09:30", "subject-a", null, null, null,
                List.of("teacher-a"), List.of(), "x".repeat(2001));
        assertThatThrownBy(() -> normalizer.normalize(request(List.of(invalidTime), 1, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CalendarBulkCreateRequest request(List<CalendarBulkRowRequest> rows, int repeatWeeks, String resolveMode) {
        return new CalendarBulkCreateRequest(
                " tenant-a ", " key-a ", " school-a ", " 2026-2027 ", "2026-07-14",
                repeatWeeks, " note ", false, resolveMode, rows);
    }

    private CalendarBulkRowRequest row(List<String> classIds, List<String> mainTeachers, List<String> assistants) {
        return new CalendarBulkRowRequest(
                " row-a ", classIds, 2, " morning ", 1, 2,
                "08:00", "09:30", " subject-a ", " level-a ", " room-a ", " Room A ",
                mainTeachers, assistants, " row note ");
    }
}
