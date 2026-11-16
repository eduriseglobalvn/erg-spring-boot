package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 07:00 every day: teachers with periods of yesterday still unconfirmed; 07:00 on the 14th: teachers with unconfirmed
 * periods of the previous month. One instance runs it (cluster lock); the outbox dedupe key keeps a repeat to one message.
 */
@Component
public class LessonReportReminderJob {

    static final String DAILY_JOB = "lms-lesson-report-reminder";
    static final String MONTHLY_JOB = "lms-lesson-report-month-reminder";
    private static final Logger log = LoggerFactory.getLogger(LessonReportReminderJob.class);

    private final LessonReportService reports;
    private final ClusterJobLock jobLock;

    LessonReportReminderJob(LessonReportService reports, ClusterJobLock jobLock) {
        this.reports = reports;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${erg.lms.lesson-report-cron:0 0 7 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void runDaily() {
        jobLock.callExclusively(DAILY_JOB, this::remindYesterday);
    }

    @Scheduled(cron = "${erg.lms.lesson-report-month-cron:0 0 7 14 * *}", zone = "Asia/Ho_Chi_Minh")
    public void runMonthly() {
        jobLock.callExclusively(MONTHLY_JOB, this::remindLastMonth);
    }

    /** Returns how many reminders were handed to the outbox. */
    public int remindYesterday() {
        LocalDate yesterday = reports.today().minusDays(1);
        return remind(yesterday, yesterday, "lesson-report:%s:" + yesterday, "Nhắc báo giảng",
                "Bạn còn %d tiết ngày " + yesterday + " chưa xác nhận trong sổ đầu bài.");
    }

    public int remindLastMonth() {
        YearMonth month = YearMonth.from(reports.today()).minusMonths(1);
        return remind(month.atDay(1), month.atEndOfMonth(), "lesson-report-month:%s:" + month,
                "Nhắc báo giảng tháng " + month, "Bạn còn %d tiết tháng " + month + " chưa xác nhận trong sổ đầu bài.");
    }

    private int remind(LocalDate from, LocalDate to, String keyFormat, String title, String bodyFormat) {
        List<String> tenants;
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            tenants = reports.tenantIds();
        }
        int sent = 0;
        for (String tenantId : tenants) {
            try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(tenantId, null, "system"))) {
                Map<String, Integer> owed = reports.unconfirmedByTeacher(tenantId, from, to, null);
                for (var teacher : owed.entrySet()) {
                    if (reports.send(tenantId, "system", keyFormat.formatted(teacher.getKey()), teacher.getKey(), title,
                            bodyFormat.formatted(teacher.getValue()))) sent++;
                }
            } catch (RuntimeException ex) {
                log.warn("Lesson report reminder failed for tenant {}: {}", tenantId, ex.getClass().getSimpleName());
            }
        }
        return sent;
    }
}
