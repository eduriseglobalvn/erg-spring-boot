package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.calendar.CalendarHolidayCancelled;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Person;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.PublishedBatch;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Slot;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One SCHEDULE_CANCELLED notification (reason HOLIDAY) per teacher or assistant whose periods a day off cancelled,
 * across every batch and school (KE_HOACH_LICH_DAY.md B11). Key {@code holiday-cancel:<holidayId>:<userId>:<millis>}.
 */
final class HolidayCancelNotificationComposer {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private record Mine(PublishedBatch batch, Slot slot) {
    }

    private HolidayCancelNotificationComposer() {
    }

    static List<NotificationCommand> compose(CalendarHolidayCancelled event, List<PublishedBatch> batches) {
        Map<String, List<Mine>> byUser = new LinkedHashMap<>();
        for (PublishedBatch batch : batches) {
            for (Slot slot : batch.slots()) {
                for (Person person : slot.people()) {
                    byUser.computeIfAbsent(person.userId(), k -> new ArrayList<>()).add(new Mine(batch, slot));
                }
            }
        }
        List<NotificationCommand> commands = new ArrayList<>();
        byUser.forEach((userId, mine) -> commands.add(one(event, userId, mine.stream().distinct().toList())));
        return commands;
    }

    private static NotificationCommand one(CalendarHolidayCancelled event, String userId, List<Mine> mine) {
        int periods = mine.stream().mapToInt(m -> m.slot().periodCount()).sum();
        LocalDate from = mine.stream().map(m -> dateOf(m.slot())).min(LocalDate::compareTo).orElseThrow();
        Set<String> schools = mine.stream().map(m -> m.batch().schoolName()).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> classNames = mine.stream().map(m -> m.slot().className()).collect(Collectors.toCollection(LinkedHashSet::new));
        PublishedBatch first = mine.get(0).batch();

        String title = periods + " tiết nghỉ lễ " + event.holidayName();
        String body = "Ngày " + DAY_MONTH.format(from) + " · " + String.join(", ", schools) + " · lớp "
                + String.join(", ", classNames) + " · tiết được huỷ, không cần báo giảng";
        Map<String, String> data = new LinkedHashMap<>();
        data.put("batchId", first.batchId());
        data.put("schoolId", first.schoolId());
        data.put("schoolName", first.schoolName());
        data.put("schoolShortName", first.schoolShortName());
        data.put("fromDate", from.toString());
        data.put("cancelledPeriods", String.valueOf(periods));
        data.put("reason", "HOLIDAY");
        data.put("holidayId", event.holidayId());
        data.put("holidayName", event.holidayName());
        return new NotificationCommand(event.tenantId(), event.actorId(),
                "holiday-cancel:" + event.holidayId() + ":" + userId + ":" + event.occurredAt().toInstant().toEpochMilli(),
                "SCHEDULE_CANCELLED", "teaching_schedule", first.batchId(), title, body,
                "/teacher/calendar?date=" + from, "normal", "lms", List.of(userId), null, data);
    }

    private static LocalDate dateOf(Slot slot) {
        return slot.startAt().atZoneSameInstant(ZONE).toLocalDate();
    }
}
