package com.erg.ergspringboot.lms.persistence;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Cut-off of the teaching-schedule access rule (KE_HOACH_LICH_DAY.md 2.4), used from SpEL in the teacher scope
 * queries: a teacher keeps a school/class while one of their PUBLISHED events there ends on or after Monday 00:00
 * (Vietnam) of the week before last. Mirrors TeachingScheduleAccessQuery.since in the calendar module.
 */
public final class ScheduleAccessWindow {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private ScheduleAccessWindow() {
    }

    public static OffsetDateTime since() {
        return OffsetDateTime.now(VN).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusDays(14).atStartOfDay(VN).toOffsetDateTime();
    }
}
