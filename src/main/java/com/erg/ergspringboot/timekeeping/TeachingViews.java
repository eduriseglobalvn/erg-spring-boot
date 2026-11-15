package com.erg.ergspringboot.timekeeping;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Request and response shapes of the teaching-hours (cong tiet day) API, KE_HOACH_CONG_BAO_GIANG.md 4.2.
 * Structured data only: the front end builds every sentence. {@code role} is {@code MAIN} or {@code ASSISTANT}.
 */
public final class TeachingViews {

    private TeachingViews() {}

    /** The caller's own sheet: month status, both role totals, weekly rows and what still needs attention. */
    public record MyTeachingMonth(
            String month, String status, LocalDate deadline, int total, int assistantTotal, List<TeachingWeek> weeks,
            List<TeachingRow> rows, List<TeachingRow> assistantRows, List<MissingReport> missingReports,
            List<CancelledPeriod> cancelled, TeachingConfirmationView confirmation, Instant closedAt) {}

    /** One school, subject and class with its period count per week column. */
    public record TeachingRow(
            String schoolName, String subjectName, String level, String className, int[] weekTotals, int total) {}

    /** A period that has happened and is not yet confirmed in the class log. */
    public record MissingReport(
            String eventId, LocalDate date, int periodStart, int periodCount, String className, String from, String to) {}

    public record CancelledPeriod(String eventId, LocalDate date, String className, int periodStart, int periodCount) {}

    /** {@code version} is the value to send back as {@code expectedVersion}. */
    public record TeachingConfirmationView(
            String status, Integer confirmedTotal, Integer confirmedAssistantTotal, Instant confirmedAt, String issueNote,
            long version) {}

    public record ConfirmTeachingCommand(int total, int assistantTotal, long expectedVersion) {}

    public record ReportTeachingIssueCommand(String note) {}

    /** One period of the Total sheet; {@code weekday} is ISO 1 (Monday) to 7 (Sunday). */
    public record TeachingPeriodRow(
            LocalDate date, int weekday, int periodNo, String schoolName, String from, String to, String className,
            String level, String subjectName, String lessonContent, String teacherName) {}

    public record TeachingPeriodPage(List<TeachingPeriodRow> rows, int total, int page, int size) {}

    /** One teacher, school and subject of the monthly sheet. */
    public record TeachingMonthRow(
            String teacherId, String teacherName, String schoolName, String subjectName, String classNames,
            int[] weekTotals, int total, String note, TeachingConfirmationView confirmation) {}

    public record TeachingMonthSheet(
            String month, String status, List<TeachingWeek> weeks, List<TeachingMonthRow> rows, int confirmedCount,
            int teacherCount, Instant closedAt) {}

    /** Everything the Excel export needs for one year, both role sheets, so the browser builds the workbook. */
    public record TeachingExport(
            int year, List<TeachingPeriodRow> mainPeriods, List<TeachingPeriodRow> assistantPeriods,
            List<TeachingMonthSheet> mainMonths, List<TeachingMonthSheet> assistantMonths) {}
}
