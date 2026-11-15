package com.erg.ergspringboot.timekeeping.internal.teaching;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.CalendarHolidayPort;
import com.erg.ergspringboot.calendar.CalendarOperations;
import com.erg.ergspringboot.calendar.LessonReportCounter;
import com.erg.ergspringboot.calendar.TeachingScheduleOperations;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.calendar.TeachingScheduleITBase;
import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.timekeeping.TeachingViews.ConfirmTeachingCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.MyTeachingMonth;
import com.erg.ergspringboot.timekeeping.TeachingViews.ReportTeachingIssueCommand;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CBG B6 on a real database: counting by role, cancelled and draft periods, confirmation, going back to PENDING when
 * the timetable moves, HR close with its snapshot, and the lesson-report counter the calendar asks for. The clock is
 * Thu 12/11/2026, so October 2026 is over.
 */
class TeachingHoursIT extends TeachingScheduleITBase {

    private static final YearMonth OCT = YearMonth.of(2026, 10);

    @Autowired TeachingHoursService hours;
    @Autowired TeachingMonthService months;
    @Autowired LessonReportCounter reports;
    @Autowired CalendarOperations calendar;
    @Autowired CalendarHolidayPort holidays;
    @Autowired TeachingScheduleOperations schedule;

    private String tenant;
    private String school;
    private String classId;
    private String subject;
    private String batch;
    private String main;
    private String assistant;

    private String codeOf(Runnable action) {
        return ((ApiException) org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, action::run)).getCode();
    }

    private String publishedEvent(String date, String teacher, String assistantId) {
        return event(tenant, batch, school, classId, subject, "PUBLISHED", at(date, "08:00"), 1, teacher, assistantId, null);
    }

    /** «Công của tôi» cancelled[] shows lessons that fell through, never rows an edit or a draft rewrite superseded. */
    @Test
    void cancelledListShowsOnlyLessonsThatFellThroughAndEachCancelPathWritesItsReason() {
        tenant = id();
        school = school(tenant, "THCS Nguyen Du");
        classId = classroom(tenant, school, "6A6");
        subject = subject(tenant, "Tin hoc");
        main = user(tenant, "Nguyen Quoc Vuong");
        String admin = user(tenant, "Giao vu");
        batch = batch(tenant, school, "PUBLISHED", "HK1");
        String draftBatch = batch(tenant, school, "DRAFT", "Nhap");
        String batch2 = batch(tenant, school, "PUBLISHED", "HK1 bis");
        YearMonth nov = YearMonth.of(2026, 11);
        String holiday = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-17", "08:00"), 1, main, null, null);
        String adminCancelled = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-18", "08:00"), 1, main, null, null);
        String draftDeleted = event(tenant, draftBatch, school, classId, subject, "DRAFT", at("2026-11-19", "08:00"), 1, main, null, null);
        String replaced = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-20", "08:00"), 1, main, null, null);
        String batchCancelled = event(tenant, batch2, school, classId, subject, "PUBLISHED", at("2026-11-24", "08:00"), 1, main, null, null);
        String teacherDeleted = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-26", "08:00"), 1, main, null, null);
        String teacherDraftDeleted = event(tenant, draftBatch, school, classId, subject, "DRAFT", at("2026-11-27", "08:00"), 1, main, null, null);
        String kept = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-30", "08:00"), 1, main, null, null);

        holidays.cancelPeriods(tenant, school, List.of(LocalDate.of(2026, 11, 17)), id(), "Le", admin);
        calendar.delete(tenant, new CalendarEventDeleteRequest(tenant, adminCancelled), admin);
        calendar.delete(tenant, new CalendarEventDeleteRequest(tenant, draftDeleted), admin);
        // a bulk create with DELETE_CONFLICTING writes REPLACED (unit-tested in CalendarCommandUseCaseTest); seed the row here
        write("update teaching_schedule_events set status = 'CANCELLED', cancel_reason = 'REPLACED' where id = ?", replaced);
        schedule.cancelBatch(tenant, batch2, schedule.draft(tenant, batch2).batch().version(), admin);
        calendar.deleteTeacherSchedule(tenant, main, List.of(school), "2026-11-26", "2026-11-27", admin);

        assertThat(reasonOf(holiday)).isEqualTo("HOLIDAY");
        assertThat(reasonOf(adminCancelled)).isEqualTo("ADMIN_CANCEL");
        assertThat(reasonOf(draftDeleted)).isEqualTo("DRAFT_REMOVED");
        assertThat(reasonOf(batchCancelled)).isEqualTo("BATCH_CANCELLED");
        assertThat(reasonOf(teacherDeleted)).isEqualTo("ADMIN_CANCEL");
        assertThat(reasonOf(teacherDraftDeleted)).isEqualTo("DRAFT_REMOVED");
        assertThat(reasonOf(kept)).isNull();

        MyTeachingMonth mine = hours.my(tenant, main, nov);
        assertThat(mine.total()).isEqualTo(1);
        assertThat(mine.cancelled()).extracting(c -> c.eventId())
                .containsExactlyInAnyOrder(holiday, adminCancelled, batchCancelled, teacherDeleted);
    }

    private String reasonOf(String eventId) {
        return jdbc.queryForObject("select cancel_reason from teaching_schedule_events where id = ?", String.class, eventId);
    }

    @Test
    void theMonthIsCountedPerRoleConfirmedReopenedByTheTimetableAndClosed() {
        tenant = id();
        school = school(tenant, "THCS Nguyen Du");
        classId = classroom(tenant, school, "6A6");
        subject = subject(tenant, "Tin hoc");
        main = user(tenant, "Nguyen Quoc Vuong");
        assistant = user(tenant, "Tran Tro Giang");
        batch = batch(tenant, school, "PUBLISHED", "HK1");
        String first = publishedEvent("2026-10-06", main, assistant);
        String second = publishedEvent("2026-10-13", main, null);
        String cancelled = event(tenant, batch, school, classId, subject, "CANCELLED", at("2026-10-14", "08:00"), 1, main, null, null);
        write("update teaching_schedule_events set cancel_reason = 'ADMIN_CANCEL' where id = ?", cancelled);
        event(tenant, batch, school, classId, subject, "DRAFT", at("2026-10-15", "08:00"), 1, main, null, null);

        // counting: cancelled and draft periods never count, the assistant has a sheet of their own
        MyTeachingMonth mine = hours.my(tenant, main, OCT);
        assertThat(mine.total()).isEqualTo(2);
        assertThat(mine.assistantTotal()).isZero();
        assertThat(mine.weeks()).extracting(w -> w.label()).containsExactly("01-02", "05-09", "12-16", "19-23", "26-31");
        assertThat(mine.rows()).hasSize(1);
        assertThat(mine.rows().get(0).weekTotals()).containsExactly(0, 1, 1, 0, 0);
        assertThat(mine.cancelled()).extracting(c -> c.eventId()).containsExactly(cancelled);
        assertThat(mine.deadline()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(mine.status()).isEqualTo("COLLECTING");
        assertThat(mine.missingReports()).hasSize(2);
        MyTeachingMonth helper = hours.my(tenant, assistant, OCT);
        assertThat(helper.total()).isZero();
        assertThat(helper.assistantTotal()).isEqualTo(1);
        assertThat(helper.assistantRows()).hasSize(1);
        assertThat(hours.monthSheet(tenant, OCT, "MAIN").rows()).extracting(r -> r.teacherName()).containsExactly("Nguyen Quoc Vuong");
        assertThat(hours.monthSheet(tenant, OCT, "ASSISTANT").rows()).extracting(r -> r.teacherName()).containsExactly("Tran Tro Giang");
        assertThat(hours.monthSheet(tenant, OCT, "MAIN").teacherCount()).isEqualTo(2);

        var page = hours.periods(tenant, "MAIN", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, null, 0);
        assertThat(page.total()).isEqualTo(2);
        assertThat(mine.closedAt()).isNull();
        assertThat(hours.monthSheet(tenant, OCT, "MAIN").closedAt()).isNull();
        var myPage = hours.myPeriods(tenant, main, OCT, "MAIN", 0);
        assertThat(myPage.total()).isEqualTo(2);
        assertThat(myPage.rows()).extracting(r -> r.teacherName()).containsOnly("Nguyen Quoc Vuong");
        assertThat(hours.myPeriods(tenant, main, OCT, "ASSISTANT", 0).total()).isZero();
        assertThat(hours.myPeriods(tenant, assistant, OCT, "ASSISTANT", 0).total()).isEqualTo(1);
        assertThat(page.rows().get(0).from()).isEqualTo("08:00");
        assertThat(page.rows().get(0).to()).isEqualTo("08:45");
        assertThat(page.rows().get(0).weekday()).isEqualTo(2);
        assertThat(hours.periods(tenant, "ASSISTANT", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, "6a6", 0).total())
                .isEqualTo(1);

        // B8 export: one JSON for the whole year, both roles, only months that have periods
        var export = hours.export(tenant, 2026);
        assertThat(export.year()).isEqualTo(2026);
        assertThat(export.mainPeriods()).hasSize(2);
        assertThat(export.assistantPeriods()).hasSize(1);
        assertThat(export.mainMonths()).extracting(m -> m.month()).containsExactly("2026-10");
        assertThat(export.assistantMonths()).extracting(m -> m.month()).containsExactly("2026-10");
        assertThat(export.mainMonths().get(0).rows()).extracting(r -> r.teacherName()).containsExactly("Nguyen Quoc Vuong");
        assertThat(hours.export(tenant, 2025).mainMonths()).isEmpty();

        // the lesson-report counter the calendar asks for reads the class log
        write("insert into lms_lesson_log_entries (id, tenant_id, event_id, lesson_date, period_no, class_id, subject_id,"
                + " teacher_user_id, ppct_period, actual_period, status, confirmed_at, version)"
                + " values (?, ?, ?, date '2026-10-06', 1, ?, ?, ?, 1, 1, 'CONFIRMED', now(), 0)", id(), tenant, first, classId, subject, main);
        assertThat(reports.reported(tenant, List.of(first, second))).isEqualTo(Set.of(first));
        assertThat(hours.my(tenant, main, OCT).missingReports()).extracting(m -> m.eventId()).containsExactly(second);

        // confirmation checks the numbers and the version
        assertThat(codeOf(() -> months.confirm(tenant, main, OCT, new ConfirmTeachingCommand(3, 0, 0)))).isEqualTo("TOTAL_CHANGED");
        assertThat(codeOf(() -> months.confirm(tenant, main, OCT, new ConfirmTeachingCommand(2, 0, 9)))).isEqualTo("STALE_VERSION");
        assertThat(codeOf(() -> months.confirm(tenant, user(tenant, "Khong day"), OCT, new ConfirmTeachingCommand(0, 0, 0))))
                .isEqualTo("NO_TEACHING_PERIODS");
        assertThat(months.confirm(tenant, main, OCT, new ConfirmTeachingCommand(2, 0, 0)).status()).isEqualTo("CONFIRMED");
        assertThat(codeOf(() -> months.reportIssue(tenant, main, OCT, new ReportTeachingIssueCommand("  ")))).isEqualTo("NOTE_REQUIRED");

        // the timetable adds a period: the confirmed teacher goes back to PENDING and the sheet shows the new total
        publishedEvent("2026-10-20", main, null);
        assertThat(months.recheck(tenant, OCT)).isEqualTo(1);
        MyTeachingMonth changed = hours.my(tenant, main, OCT);
        assertThat(changed.total()).isEqualTo(3);
        assertThat(changed.confirmation().status()).isEqualTo("PENDING");

        // HR cannot close until everyone confirmed, then the snapshot freezes both sheets
        assertThat(codeOf(() -> months.close(tenant, "hr", OCT))).isEqualTo("NOT_ALL_CONFIRMED");
        months.confirm(tenant, main, OCT, new ConfirmTeachingCommand(3, 0, changed.confirmation().version()));
        months.reportIssue(tenant, assistant, OCT, new ReportTeachingIssueCommand("Thieu tiet"));
        assertThat(hours.my(tenant, assistant, OCT).confirmation().status()).isEqualTo("ISSUE_REPORTED");
        assertThat(codeOf(() -> months.close(tenant, "hr", OCT))).isEqualTo("NOT_ALL_CONFIRMED");
        months.confirm(tenant, assistant, OCT, new ConfirmTeachingCommand(0, 1, hours.my(tenant, assistant, OCT).confirmation().version()));
        assertThat(hours.monthSheet(tenant, OCT, "MAIN").status()).isEqualTo("READY");
        months.close(tenant, "hr", OCT);

        publishedEvent("2026-10-21", main, null);
        MyTeachingMonth frozen = hours.my(tenant, main, OCT);
        assertThat(frozen.status()).isEqualTo("CLOSED");
        assertThat(frozen.total()).isEqualTo(3);
        assertThat(frozen.closedAt()).isNotNull();
        assertThat(hours.monthSheet(tenant, OCT, "MAIN").closedAt()).isEqualTo(frozen.closedAt());
        assertThat(hours.monthSheet(tenant, OCT, "MAIN").rows().get(0).total()).isEqualTo(3);
        assertThat(hours.monthSheet(tenant, OCT, "ASSISTANT").rows().get(0).total()).isEqualTo(1);
        assertThat(hours.monthSheet(tenant, OCT, "MAIN").status()).isEqualTo("CLOSED");
        assertThat(codeOf(() -> months.confirm(tenant, main, OCT, new ConfirmTeachingCommand(4, 0, 0)))).isEqualTo("MONTH_CLOSED");
        assertThat(jdbc.queryForObject("select count(*) from tk_audit_log where tenant_id = ? and action = 'CLOSE_TEACHING_MONTH'",
                Integer.class, tenant)).isEqualTo(1);
        assertThat(hours.export(tenant, 2026).mainMonths()).extracting(m -> m.month()).contains("2026-10");
    }
}
