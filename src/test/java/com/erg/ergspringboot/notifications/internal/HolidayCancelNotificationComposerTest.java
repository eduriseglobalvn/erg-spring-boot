package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.CalendarHolidayCancelled;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Person;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.PublishedBatch;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Slot;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** One notification per teacher or assistant for the whole day off, with a key that a redelivery repeats exactly. */
class HolidayCancelNotificationComposerTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-12T01:00:00Z");

    private static Slot slot(String id, String cls, int hour, Person... people) {
        return new Slot(id, "c-" + cls, cls, OffsetDateTime.parse("2026-11-20T0" + hour + ":00:00+07:00"), hour, 1, "P1", List.of(people));
    }

    @Test
    void everyTeacherGetsOneNotificationAcrossBatchesAndSchools() {
        Person tuan = new Person("u-tuan", "Tuấn", "MAIN");
        Person lan = new Person("u-lan", "Lan", "ASSISTANT");
        PublishedBatch a = new PublishedBatch("b1", "s1", "THCS Kết Đoàn", "Kết Đoàn", "PUBLISHED",
                List.of(slot("e1", "6A1", 7, tuan, lan), slot("e2", "6A2", 8, tuan)));
        PublishedBatch b = new PublishedBatch("b2", "s2", "THCS Nguyễn Du", "Nguyễn Du", "PUBLISHED",
                List.of(slot("e3", "7A1", 7, tuan)));
        CalendarHolidayCancelled event = new CalendarHolidayCancelled("holiday-cancel:h1:1", "t", "h1", "Ngày Nhà giáo Việt Nam",
                List.of("e1", "e2", "e3"), "admin", AT, 1);

        List<NotificationCommand> first = HolidayCancelNotificationComposer.compose(event, List.of(a, b));
        List<NotificationCommand> again = HolidayCancelNotificationComposer.compose(event, List.of(a, b));

        assertThat(first).hasSize(2);
        NotificationCommand forTuan = first.stream().filter(c -> c.userIds().contains("u-tuan")).findFirst().orElseThrow();
        assertThat(forTuan.eventType()).isEqualTo("SCHEDULE_CANCELLED");
        assertThat(forTuan.title()).isEqualTo("3 tiết nghỉ lễ Ngày Nhà giáo Việt Nam");
        assertThat(forTuan.data()).containsEntry("reason", "HOLIDAY").containsEntry("holidayName", "Ngày Nhà giáo Việt Nam");
        assertThat(forTuan.idempotencyKey()).isEqualTo("holiday-cancel:h1:u-tuan:" + AT.toInstant().toEpochMilli());
        assertThat(first.stream().map(NotificationCommand::idempotencyKey).toList())
                .isEqualTo(again.stream().map(NotificationCommand::idempotencyKey).toList()); // a retry never duplicates
        assertThat(first.stream().filter(c -> c.userIds().contains("u-lan")).findFirst().orElseThrow().title())
                .isEqualTo("1 tiết nghỉ lễ Ngày Nhà giáo Việt Nam"); // the assistant is told too
    }
}
