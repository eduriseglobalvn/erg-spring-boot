package com.erg.ergspringboot.timekeeping.internal.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.calendar.TeachingScheduleITBase;
import com.erg.ergspringboot.calendar.TeachingScheduleOperations;
import com.erg.ergspringboot.timekeeping.DayPart;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cancelling a published batch only cancels periods that have not started: what timekeeping reads for past days
 * (leave impact, self view, punch context, today's dashboard) is the same before and after. Clock: Thu 12/11/2026 08:00.
 */
class BatchCancelKeepsPastPeriodsIT extends TeachingScheduleITBase {

    @Autowired CalendarReadPort calendar;
    @Autowired AffectedPeriodsService affected;
    @Autowired TeachingScheduleOperations schedule;

    @Test
    void pastPeriodsSurviveABatchCancelWhileFutureOnesDisappearFromTimekeepingReads() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        String classId = classroom(tenant, school, "6A6");
        String subject = subject(tenant, "Tin hoc");
        String teacher = user(tenant, "Nguyen Quoc Vuong");
        String admin = user(tenant, "Giao vu");
        String batch = batch(tenant, school, "PUBLISHED", "HK1");
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-10", "08:00"), 1, teacher, null, null);
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-11", "08:00"), 1, teacher, null, null);
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-13", "08:00"), 1, teacher, null, null);
        LocalDate from = LocalDate.parse("2026-11-09");
        LocalDate to = LocalDate.parse("2026-11-15");
        LocalDate pastTo = LocalDate.parse("2026-11-11");

        List<TeacherPeriod> pastBefore = calendar.teacherPeriods(tenant, teacher, from, pastTo);
        assertThat(pastBefore).hasSize(2);
        assertThat(calendar.teacherPeriods(tenant, teacher, from, to)).hasSize(3);

        schedule.cancelBatch(tenant, batch, schedule.draft(tenant, batch).batch().version(), admin);

        assertThat(calendar.teacherPeriods(tenant, teacher, from, pastTo)).isEqualTo(pastBefore);
        assertThat(affected.affected(tenant, teacher, from, pastTo, DayPart.FULL)).isEqualTo(pastBefore);
        // the period of Friday had not started: gone for leave impact and for the "period starting soon" check
        assertThat(calendar.teacherPeriods(tenant, teacher, from, to)).hasSize(2);
        assertThat(calendar.teachersWithPeriodStarting(tenant, Set.of(teacher),
                at("2026-11-13", "07:00").toInstant(), at("2026-11-13", "09:00").toInstant())).isEmpty();
        assertThat(calendar.teachersWithPeriodStarting(tenant, Set.of(teacher),
                at("2026-11-10", "07:00").toInstant(), at("2026-11-10", "09:00").toInstant())).containsExactly(teacher);
    }
}
