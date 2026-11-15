package com.erg.ergspringboot.timekeeping.internal.teaching;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleFixtures;
import com.erg.ergspringboot.lms.LessonReportNotifier;
import com.erg.ergspringboot.timekeeping.TeachingViews.ConfirmTeachingCommand;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** B1 regression with the REAL notification service: the "system" actor has no users row, so it must not hit the FK. */
class TeachingMonthNotificationIT extends TeachingScheduleFixtures {

    private static final YearMonth OCT = YearMonth.of(2026, 10);

    @Autowired TeachingHoursService hours;
    @Autowired TeachingMonthService months;
    @Autowired LessonReportNotifier notifier;

    @Test
    void scheduleChangeAfterConfirmationResetsTheTeacherAndNotifiesWithoutAUsersRowForTheSystemActor() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        String classId = classroom(tenant, school, "6A6");
        String subject = subject(tenant, "Tin hoc");
        String teacher = user(tenant, "Nguyen Quoc Vuong");
        String batch = batch(tenant, school, "PUBLISHED", "HK1");
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-10-06", "08:00"), 1, teacher, null, null);
        String second = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-10-13", "08:00"), 1, teacher, null, null);
        months.confirm(tenant, teacher, OCT, new ConfirmTeachingCommand(2, 0, 0));

        write("update teaching_schedule_events set status = 'CANCELLED', cancel_reason = 'HOLIDAY' where id = ?", second);

        assertThat(months.recheckOpenMonths(tenant)).isEqualTo(1);
        assertThat(hours.my(tenant, teacher, OCT).confirmation().status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from notification_recipients r join notification_messages m on m.id = r.notification_id"
                + " where m.tenant_id = ? and r.user_id = ? and m.created_by is null", Integer.class, tenant, teacher)).isEqualTo(1);
    }

    @Test
    void aFailingNoticeNeverRollsBackTheCallersTransaction() {
        String tenant = id();
        String teacher = user(tenant, "Nguyen Quoc Vuong");
        Integer result = transactions.execute(status -> {
            try {
                notifier.remind(tenant, "no-such-user", "k:" + id(), teacher, "t", "b"); // created_by FK violation
            } catch (RuntimeException expected) {
                // the caller swallows it, exactly as TeachingMonthService/LessonReportService do
            }
            return 1;
        });
        assertThat(result).isEqualTo(1);
    }
}
