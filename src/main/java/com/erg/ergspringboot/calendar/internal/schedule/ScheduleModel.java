package com.erg.ergspringboot.calendar.internal.schedule;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** Value types the schedule conflict engine works on. */
final class ScheduleModel {

    private ScheduleModel() {
    }

    record TeacherRef(String id, String name, String role) {}

    /**
     * One concrete period on one date. {@code mine} marks occurrences being drafted or published now; the rest are
     * already-published events they are compared against. {@code rowKey} groups the weekly repeats of one grid row.
     */
    record Occurrence(String eventId, String rowKey, String clientRowId, String batchId, String batchLabel,
            String batchStatus, String schoolId, String schoolName, String classId, String className, String roomId,
            String roomName, String subjectName, List<TeacherRef> teachers, LocalDate date, int weekday, int periodNo,
            OffsetDateTime start, OffsetDateTime end, String createdByName, String createdByRole,
            OffsetDateTime createdAt, boolean mine) {

        boolean overlaps(Occurrence other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }

        boolean sameEvent(Occurrence other) {
            return eventId != null && Objects.equals(eventId, other.eventId);
        }

        boolean hasTeacher(String teacherId) {
            return teachers.stream().anyMatch(t -> t.id().equals(teacherId));
        }
    }

    record Leave(String userId, LocalDate from, LocalDate to, String part) {
        boolean covers(Occurrence o) {
            if (o.date().isBefore(from) || o.date().isAfter(to)) return false;
            // same rule as timekeeping AffectedPeriodsService: the session is the start time against noon
            boolean morning = o.start().atZoneSameInstant(ScheduleDataLoader.VN).getHour() < 12;
            return switch (part) {
                case "AM" -> morning;
                case "PM" -> !morning;
                default -> true;
            };
        }
    }

    record RoomRef(String id, String name, Integer seatCount) {}

    record SchoolRef(String id, String name) {}

    record ClassRef(String id, String name, Integer grade) {}
}
