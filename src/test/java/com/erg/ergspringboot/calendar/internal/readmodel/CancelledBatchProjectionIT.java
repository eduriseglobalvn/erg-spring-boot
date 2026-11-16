package com.erg.ergspringboot.calendar.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.PublishedBatch;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Slot;
import com.erg.ergspringboot.calendar.TeachingScheduleITBase;
import com.erg.ergspringboot.calendar.TeachingScheduleOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The batch-cancel notice lists exactly the periods this cancel wrote (cancel_reason), not any period cancelled nearby. */
class CancelledBatchProjectionIT extends TeachingScheduleITBase {

    @Autowired CalendarPublishedBatchProjection projection;
    @Autowired TeachingScheduleOperations schedule;

    @Test
    void onlyPeriodsCancelledByTheBatchCancelAreListed() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        String classId = classroom(tenant, school, "9A1");
        String subject = subject(tenant, "Tin hoc");
        String teacher = user(tenant, "Nguyen Quoc Vuong");
        String admin = user(tenant, "Giao vu");
        String batch = batch(tenant, school, "PUBLISHED", "HK1");
        String byBatchOne = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-20", "08:00"), 1, teacher, null, null);
        String byBatchTwo = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-27", "08:00"), 1, teacher, null, null);
        String earlier = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-25", "08:00"), 1, teacher, null, null);
        write("update teaching_schedule_batches set published_at = now() where id = ?", batch);
        // a single period an admin cancelled just before (same second as the batch cancel): not part of the batch notice
        write("update teaching_schedule_events set status = 'CANCELLED', cancel_reason = 'ADMIN_CANCEL', updated_at = ? where id = ?",
                java.sql.Timestamp.from(NOW), earlier);

        schedule.cancelBatch(tenant, batch, schedule.draft(tenant, batch).batch().version(), admin);

        PublishedBatch listed = projection.findCancelledBatch(tenant, batch, NOW.atOffset(java.time.ZoneOffset.UTC)).orElseThrow();
        assertThat(listed.slots()).extracting(Slot::eventId).containsExactlyInAnyOrder(byBatchOne, byBatchTwo);
    }
}
