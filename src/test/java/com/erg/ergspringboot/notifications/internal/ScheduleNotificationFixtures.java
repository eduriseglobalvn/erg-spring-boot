package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Person;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.PublishedBatch;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Slot;
import java.time.OffsetDateTime;
import java.util.List;

/** Published batch: Wed 11/11/2026 periods 6-7 (9A2, main t1 + assistant t2) and Thu 12/11 period 3 (9A4, t1). */
public final class ScheduleNotificationFixtures {

    private ScheduleNotificationFixtures() {
    }

    public static PublishedBatch published() {
        return new PublishedBatch("batch-1", "school-1", "THCS Trương Công Định", "TCĐ", "PUBLISHED", List.of(
                new Slot("e1", "c2", "9A2", OffsetDateTime.parse("2026-11-11T13:00:00+07:00"), 6, 2, "P.101",
                        List.of(new Person("t1", "Nguyễn Văn An", "MAIN"), new Person("t2", "Trần Thị Bình", "ASSISTANT"))),
                new Slot("e2", "c4", "9A4", OffsetDateTime.parse("2026-11-12T09:00:00+07:00"), 3, 1, null,
                        List.of(new Person("t1", "Nguyễn Văn An", "MAIN")))));
    }
}
