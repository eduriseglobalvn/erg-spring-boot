package com.erg.ergspringboot.timekeeping.internal.teaching;

import com.erg.ergspringboot.calendar.TeachingPeriodReadPort.TeachingPeriod;
import com.erg.ergspringboot.timekeeping.TeachingWeek;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;

/** Pure counting rules of the teaching-hours sheets: one event is {@code periodCount} periods, one role per sheet. */
final class TeachingSheets {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String MAIN = "MAIN";
    static final String ASSISTANT = "ASSISTANT";

    private TeachingSheets() {}

    /** One timetable period of an event. */
    record Tiet(TeachingPeriod event, int periodNo, LocalDate date, Instant from, Instant to) {}

    /** One teacher, school and subject of a month: the classes and the periods per week column. */
    record Line(String teacherId, String teacherName, String schoolId, String schoolName, String subjectId,
                String subjectName, TreeSet<String> classes, int[] weekTotals) {
        int total() {
            int sum = 0;
            for (int w : weekTotals) sum += w;
            return sum;
        }
    }

    static String role(String role) {
        if (role == null || role.isBlank() || MAIN.equalsIgnoreCase(role)) return MAIN;
        if (ASSISTANT.equalsIgnoreCase(role)) return ASSISTANT;
        throw new TimekeepingException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Vai trò phải là MAIN hoặc ASSISTANT");
    }

    static LocalDate dateOf(TeachingPeriod event) {
        return event.start().atZone(ZONE).toLocalDate();
    }

    static List<TeachingPeriod> ofRole(List<TeachingPeriod> events, String role) {
        return events.stream().filter(e -> role.equals(e.role())).toList();
    }

    /** One {@link Tiet} per period; a multi-period event is spread evenly (shortcut: the timetable keeps one start/end per event). */
    static List<Tiet> expand(List<TeachingPeriod> events) {
        List<Tiet> result = new ArrayList<>();
        for (TeachingPeriod e : events) {
            int count = Math.max(1, e.periodCount());
            long each = Duration.between(e.start(), e.end()).toMillis() / count;
            for (int k = 0; k < count; k++) {
                Instant from = e.start().plusMillis(each * k);
                result.add(new Tiet(e, e.periodStart() + k, dateOf(e), from, from.plusMillis(each)));
            }
        }
        return result;
    }

    /** Index into {@code weeks} of the column that counts {@code date}, or -1 outside the month. */
    static int weekIndex(List<TeachingWeek> weeks, LocalDate date) {
        for (int i = 0; i < weeks.size(); i++) {
            if (!date.isBefore(weeks.get(i).from()) && !date.isAfter(weeks.get(i).to())) return i;
        }
        return -1;
    }

    /** Events of one role grouped by teacher, school and subject, in first-seen order. */
    static List<Line> lines(List<TeachingPeriod> events, List<TeachingWeek> weeks) {
        Map<String, Line> byKey = new LinkedHashMap<>();
        for (TeachingPeriod e : events) {
            int week = weekIndex(weeks, dateOf(e));
            if (week < 0) continue;
            Line line = byKey.computeIfAbsent(e.teacherUserId() + '|' + e.schoolId() + '|' + e.subjectId(),
                    k -> new Line(e.teacherUserId(), e.teacherName(), e.schoolId(), e.schoolName(), e.subjectId(),
                            e.subjectName(), new TreeSet<>(), new int[weeks.size()]));
            if (e.className() != null && !e.className().isBlank()) line.classes().add(e.className());
            line.weekTotals()[week] += e.periodCount();
        }
        return List.copyOf(byKey.values());
    }

    /** Periods of {@code teacherId} on the given (already role-filtered) events. */
    static int total(List<TeachingPeriod> events, String teacherId) {
        return events.stream().filter(e -> teacherId.equals(e.teacherUserId())).mapToInt(TeachingPeriod::periodCount).sum();
    }
}
