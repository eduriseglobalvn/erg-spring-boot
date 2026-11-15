package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Person;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.PublishedBatch;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.Slot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/** Builds one Vietnamese notification per teacher or assistant of a schedule batch (KE_HOACH_LICH_DAY.md B9). */
final class ScheduleNotificationComposer {

    enum Kind { PUBLISHED, CHANGED, CANCELLED, REMOVED }

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final ObjectMapper JSON = new ObjectMapper();

    private ScheduleNotificationComposer() {
    }

    /** Idempotency key = keyPrefix:userId:keyVersion, so a retried event never duplicates. */
    static List<NotificationCommand> compose(Kind kind, String tenantId, String actorId, String keyPrefix, long keyVersion,
            String eventType, PublishedBatch batch) {
        Map<String, List<Slot>> byUser = new LinkedHashMap<>();
        for (Slot slot : batch.slots()) {
            for (Person person : slot.people()) {
                List<Slot> mine = byUser.computeIfAbsent(person.userId(), k -> new ArrayList<>());
                if (!mine.contains(slot)) mine.add(slot);
            }
        }
        List<NotificationCommand> commands = new ArrayList<>();
        byUser.forEach((userId, slots) -> commands.add(
                one(kind, tenantId, actorId, keyPrefix + ":" + userId + ":" + keyVersion, eventType, batch, userId, slots)));
        return commands;
    }

    private static NotificationCommand one(Kind kind, String tenantId, String actorId, String key, String eventType,
            PublishedBatch batch, String userId, List<Slot> slots) {
        Set<String> roles = slots.stream().flatMap(s -> s.people().stream()).filter(p -> userId.equals(p.userId()))
                .map(Person::role).collect(Collectors.toSet());
        boolean main = roles.contains("MAIN");
        boolean assistant = roles.contains("ASSISTANT");
        String role = main && assistant ? "BOTH" : assistant ? "ASSISTANT" : "MAIN";
        boolean assistantOnly = "ASSISTANT".equals(role);

        int periods = slots.stream().mapToInt(Slot::periodCount).sum();
        int assistantPeriods = slots.stream()
                .filter(s -> s.people().stream().anyMatch(p -> userId.equals(p.userId()) && "ASSISTANT".equals(p.role())))
                .mapToInt(Slot::periodCount).sum();
        LocalDate from = slots.stream().map(ScheduleNotificationComposer::dateOf).min(Comparator.naturalOrder()).orElseThrow();
        List<Slot> firstDay = slots.stream().filter(s -> dateOf(s).equals(from)).toList();
        String when = DAY_MONTH.format(from) + " (" + weekdayName(from.getDayOfWeek()) + ", " + range(firstDay) + ")";
        String classNames = classes(slots).values().stream().collect(Collectors.joining(", "));
        String school = batch.schoolName();

        String title;
        String body;
        switch (kind) {
            case PUBLISHED -> {
                title = periods + (assistantOnly ? " tiết trợ giảng mới ở " : " tiết mới ở ") + school;
                String assisting = assistantOnly ? " · trợ giảng cho " + mainNames(slots) : "";
                body = "Từ " + when + assisting + " · được cấp quyền lớp " + classNames;
            }
            case CHANGED -> {
                Slot slot = slots.get(0);
                title = "Tiết dạy thay đổi ở " + school;
                String room = slot.roomName() == null || slot.roomName().isBlank() ? "" : " · phòng " + slot.roomName();
                body = slots.size() == 1 ? "Tiết " + when + " lớp " + slot.className() + " đã được cập nhật" + room
                        : periods + " tiết từ " + when + " lớp " + classNames + " đã được cập nhật";
            }
            case REMOVED -> {
                title = "Tiết dạy được chuyển đi ở " + school;
                body = periods + " tiết từ " + when + " lớp " + classNames + " không còn trong lịch của bạn";
            }
            default -> {
                title = periods + " tiết bị hủy ở " + school;
                body = "Đợt lịch đã bị hủy · từ " + when + " · lớp " + classNames;
            }
        }

        Map<String, String> data = new LinkedHashMap<>();
        data.put("batchId", batch.batchId());
        data.put("schoolId", batch.schoolId());
        data.put("schoolName", school);
        data.put("schoolShortName", batch.schoolShortName());
        data.put("fromDate", from.toString());
        data.put(switch (kind) {
            case PUBLISHED -> "newPeriods";
            case CHANGED -> "changedPeriods";
            case REMOVED -> "removedPeriods";
            case CANCELLED -> "cancelledPeriods";
        }, String.valueOf(periods));
        data.put("weekdays", JSON.writeValueAsString(slots.stream()
                .map(s -> dateOf(s).getDayOfWeek().getValue()).distinct().sorted().toList()));
        data.put("periodRange", periodBounds(slots));
        data.put("classes", JSON.writeValueAsString(classes(slots).entrySet().stream()
                .map(e -> {
                    Map<String, String> cls = new LinkedHashMap<>(); // Map.of order varies per JVM run
                    cls.put("id", e.getKey());
                    cls.put("name", e.getValue());
                    return cls;
                }).toList()));
        data.put("role", role);
        if (assistantPeriods > 0) data.put("assistantPeriods", String.valueOf(assistantPeriods));
        if (kind == Kind.CHANGED || kind == Kind.REMOVED) data.put("eventId", slots.get(0).eventId());
        if (kind == Kind.CANCELLED) data.put("reason", "BATCH_CANCELLED");

        return new NotificationCommand(tenantId, actorId, key, eventType, "teaching_schedule", batch.batchId(),
                title, body, "/teacher/calendar?date=" + from, "normal", "lms", List.of(userId), null, data);
    }

    private static LocalDate dateOf(Slot slot) {
        return slot.startAt().atZoneSameInstant(ZONE).toLocalDate();
    }

    private static String weekdayName(DayOfWeek day) {
        return day == DayOfWeek.SUNDAY ? "Chủ nhật" : "Thứ " + (day.getValue() + 1);
    }

    private static String range(List<Slot> slots) {
        String bounds = periodBounds(slots);
        return bounds.contains("-") ? "tiết " + bounds.replace("-", "–") : "tiết " + bounds;
    }

    private static String periodBounds(List<Slot> slots) {
        int a = slots.stream().mapToInt(Slot::periodStart).min().orElse(0);
        int b = slots.stream().mapToInt(s -> s.periodStart() + s.periodCount() - 1).max().orElse(a);
        return a == b ? String.valueOf(a) : a + "-" + b;
    }

    /** id -> name, sorted by name. */
    private static Map<String, String> classes(List<Slot> slots) {
        Map<String, String> byId = new TreeMap<>();
        slots.forEach(s -> byId.put(s.classId(), s.className()));
        return byId.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x, LinkedHashMap::new));
    }

    private static String mainNames(List<Slot> slots) {
        Set<String> names = new LinkedHashSet<>();
        slots.forEach(s -> s.people().stream().filter(p -> "MAIN".equals(p.role()))
                .map(Person::name).filter(n -> n != null && !n.isBlank()).forEach(names::add));
        return String.join(", ", names);
    }
}
