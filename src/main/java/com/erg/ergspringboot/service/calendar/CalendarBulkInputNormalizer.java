package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CalendarBulkInputNormalizer {

    static final int MAX_ROWS = 200;
    static final int MAX_REPEAT_WEEKS = 52;
    static final int MAX_CLASSES_PER_ROW = 20;
    static final int MAX_TEACHERS_PER_ROLE = 20;
    static final int MAX_NOTE_LENGTH = 2_000;
    static final int MAX_ROOM_NAME_LENGTH = 255;

    public CalendarBulkCreateRequest normalize(CalendarBulkCreateRequest input) {
        if (input == null) throw new IllegalArgumentException("Calendar bulk input is required.");
        if (input.rows() == null || input.rows().isEmpty()) {
            throw new IllegalArgumentException("rows must contain at least one schedule row.");
        }
        if (input.rows().size() > MAX_ROWS) {
            throw new IllegalArgumentException("rows must not exceed " + MAX_ROWS + ".");
        }
        if (input.repeatWeeks() < 1 || input.repeatWeeks() > MAX_REPEAT_WEEKS) {
            throw new IllegalArgumentException("repeatWeeks must be between 1 and " + MAX_REPEAT_WEEKS + ".");
        }
        parseDate(input.applyFrom(), "applyFrom");
        requireLength(input.note(), MAX_NOTE_LENGTH, "note");

        List<CalendarBulkRowRequest> rows = input.rows().stream().map(this::normalizeRow).toList();
        return new CalendarBulkCreateRequest(
                trimToNull(input.tenantId()), trimToNull(input.idempotencyKey()),
                requireText(input.schoolId(), "schoolId"), trimToNull(input.academicYear()),
                input.applyFrom().trim(), input.repeatWeeks(), trimToNull(input.note()),
                input.overrideConflicts(), upper(input.resolveMode()), rows);
    }

    private CalendarBulkRowRequest normalizeRow(CalendarBulkRowRequest row) {
        if (row == null) throw new IllegalArgumentException("Schedule row must not be null.");
        List<String> classIds = normalizeIds(row.classIds());
        List<String> mainTeachers = normalizeIds(row.mainTeacherIds());
        List<String> assistants = normalizeIds(row.assistantTeacherIds());
        requireMax(classIds, MAX_CLASSES_PER_ROW, "classIds");
        requireMax(mainTeachers, MAX_TEACHERS_PER_ROLE, "mainTeacherIds");
        requireMax(assistants, MAX_TEACHERS_PER_ROLE, "assistantTeacherIds");
        String startTime = parseTime(row.startTime(), "startTime");
        String endTime = parseTime(row.endTime(), "endTime");
        requireLength(row.roomName(), MAX_ROOM_NAME_LENGTH, "roomName");
        requireLength(row.note(), MAX_NOTE_LENGTH, "row note");
        return new CalendarBulkRowRequest(
                requireText(row.clientRowId(), "clientRowId"), classIds, row.weekday(), upper(row.session()),
                row.periodStart(), row.periodCount(), startTime, endTime,
                requireText(row.subjectId(), "subjectId"), trimToNull(row.levelId()), trimToNull(row.roomId()),
                trimToNull(row.roomName()), mainTeachers, assistants, trimToNull(row.note()));
    }

    private static List<String> normalizeIds(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String item = trimToNull(value);
            if (item != null) normalized.add(item);
        }
        return List.copyOf(normalized);
    }

    private static void requireMax(List<String> values, int max, String field) {
        if (values.size() > max) throw new IllegalArgumentException(field + " must not exceed " + max + ".");
    }

    private static void requireLength(String value, int max, String field) {
        if (value != null && value.trim().length() > max) {
            throw new IllegalArgumentException(field + " must not exceed " + max + " characters.");
        }
    }

    private static void parseDate(String value, String field) {
        try {
            LocalDate.parse(requireText(value, field));
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException(field + " must use ISO date format yyyy-MM-dd.", error);
        }
    }

    private static String parseTime(String value, String field) {
        String normalized = requireText(value, field);
        try {
            return LocalTime.parse(normalized).toString();
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException(field + " must use ISO time format HH:mm.", error);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(field + " is required.");
        return normalized;
    }

    private static String upper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
