package com.erg.ergspringboot.timekeeping.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.ReviewDecision;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ActionItem;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.Bucket;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PunchReviewCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.TodayDashboard;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TodayDashboardServiceIT extends AdminITBase {

    private static final String T = "tk-today-it";
    private static final String SCHOOL = "sch_today";
    private static final Instant SEVEN_LOCAL = Instant.parse("2026-11-12T00:00:00Z"); // 07:00 in Vietnam

    @Autowired TodayDashboardService dashboard;
    @Autowired PunchReviewService reviews;

    private String flagged;

    @BeforeEach
    void seed() {
        jdbc.update("insert into education_units (id, tenant_id, name, slug, type, status, created_at, updated_at)"
                + " values (?, ?, 'Cơ sở A', ?, 'school', 'active', now(), now())", SCHOOL, T, SCHOOL);
        jdbc.update("insert into tk_shifts (id, tenant_id, school_id, name, start_time, end_time, work_units, weekdays)"
                + " values ('shift_today', ?, ?, 'Ca sáng', '07:30', '11:30', 1.0, '{1,2,3,4,5}')", T, SCHOOL);
        for (int i = 0; i < 130; i++) teacher(T, u(i), SCHOOL, "Giáo viên " + i);
        for (int i = 0; i < 112; i++) { // IN at 07:mm with mm = i % 60
            String id = punch(T, SCHOOL, u(i), "IN", SEVEN_LOCAL.plusSeconds(60L * (i % 60)), i == 0 ? "FLAGGED" : "VALID");
            if (i == 0) flagged = id;
        }
        punch(T, SCHOOL, u(1), "OUT", SEVEN_LOCAL.plusSeconds(60L * 20), "VALID");
        punch(T, SCHOOL, u(120), "IN", SEVEN_LOCAL.plusSeconds(60L * 16), "REJECTED");
        int[] late = {10, 10, 8, 7, 6, 6}; // 47 minutes
        for (int i = 0; i < late.length; i++) day(T, SCHOOL, u(i), TODAY, "LATE_EARLY", "1.0", late[i], null);
        request(T, SCHOOL, u(129), "LEAVE", "ANNUAL", TODAY, TODAY, "APPROVED");
        when(calendar.teachersWithPeriodStarting(eq(T), anyCollection(), any(), any()))
                .thenReturn(Set.of(u(128), u(127), u(5)));
    }

    @Test
    void countsTheSampleDay() {
        TodayDashboard today = dashboard.today(T, SCHOOL, TODAY);

        assertThat(today.total()).isEqualTo(130);
        assertThat(today.present()).isEqualTo(112);
        assertThat(today.late()).isEqualTo(6);
        assertThat(today.lateMinutesTotal()).isEqualTo(47);
        assertThat(today.leave()).isEqualTo(1);
        assertThat(today.notPunched()).isEqualTo(17); // 130 - 112 present - 1 on leave
        assertThat(today.notPunchedWithPeriodSoon()).isEqualTo(2);
        assertThat(today.inBuckets()).contains(new Bucket(LocalTime.of(7, 15), 30, 1));
        assertThat(today.actionItems()).extracting(ActionItem::kind)
                .containsExactlyInAnyOrder("NOT_PUNCHED_PERIOD_SOON", "NOT_PUNCHED_PERIOD_SOON", "PUNCH_REVIEW");
        assertThat(today.recentPunches()).hasSize(20);
        assertThat(today.bySite()).singleElement().satisfies(s -> {
            assertThat(s.total()).isEqualTo(130);
            assertThat(s.present()).isEqualTo(112);
        });
    }

    @Test
    void rejectingAFlaggedPunchIsAuditedAndTheDayIsRebuilt() {
        var reviewed = reviews.review(T, "admin", flagged, new PunchReviewCommand(ReviewDecision.REJECT, "Không ở trường"));

        assertThat(reviewed.status()).isEqualTo(PunchStatus.REJECTED);
        flush();
        assertThat(jdbc.queryForObject("select count(*) from tk_audit_log where tenant_id = ? and action = 'REVIEW_PUNCH'",
                Integer.class, T)).isEqualTo(1);
        assertThat(dashboard.today(T, SCHOOL, TODAY).present()).isEqualTo(111);
    }

    private static String u(int i) {
        return "td_" + i;
    }
}
