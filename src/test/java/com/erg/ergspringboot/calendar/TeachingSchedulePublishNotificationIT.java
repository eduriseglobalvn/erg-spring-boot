package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleDraftInput.Row;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.DraftResultResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Publishing a batch reaches the REAL notification service: one inbox row per teacher, however many periods they get. */
class TeachingSchedulePublishNotificationIT extends TeachingScheduleFixtures {

    @Autowired TeachingScheduleOperations schedule;

    @Test
    void publishOnceGivesEachTeacherExactlyOneNotificationAndTheAdminIsTheSender() throws InterruptedException {
        String tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        String classId = classroom(tenant, school, "9A4");
        String subject = subject(tenant, "Toan");
        String anh = user(tenant, "Nguyen Van Anh");
        String binh = user(tenant, "Tran Thi Binh");
        String admin = user(tenant, "Giao vu");
        String template = schedule.periodTemplates(tenant).get(0).id();
        // Mon 16/11 .. Sun 6/12: each weekday row repeats three times
        DraftResultResponse draft = schedule.saveDraft(tenant, new TeachingScheduleDraftInput(null, school, subject, null,
                "2026-11-16", "2026-12-06", template, null, List.of(
                new Row("r1", null, 3, 1, classId, null, null, anh, null, null),
                new Row("r2", null, 4, 1, classId, null, null, anh, null, null),
                new Row("r3", null, 5, 1, classId, null, null, binh, null, null))), admin);

        schedule.publishBatch(tenant, draft.batch().id(), draft.version(), admin);

        String sql = "select count(*) from notification_recipients where tenant_id = ?";
        for (int i = 0; i < 100 && jdbc.queryForObject(sql, Integer.class, tenant) < 2; i++) Thread.sleep(200);
        Thread.sleep(1000); // a duplicate would have landed by now
        assertThat(jdbc.queryForList("select user_id from notification_recipients where tenant_id = ? order by user_id",
                String.class, tenant)).containsExactlyInAnyOrder(anh, binh);
        assertThat(jdbc.queryForList("select distinct created_by from notification_messages where tenant_id = ?",
                String.class, tenant)).containsExactly(admin);
    }
}
