package com.erg.ergspringboot.timekeeping.internal.teaching;

import com.erg.ergspringboot.timekeeping.TeachingWeek;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * The single week-splitting rule of the teaching-hours sheets (KE_HOACH_CONG_BAO_GIANG.md 2.4):
 * Monday-Sunday weeks cut at the month edges; a Saturday/Sunday-only stub at the start of the month joins the next
 * week (at the end it already belongs to the previous one), so a month has at most 5 columns. The label runs from the first school day
 * (Mon-Fri) to the Friday of that week, and the last column runs to the end of the month.
 */
public final class TeachingWeekSplitter {

    private TeachingWeekSplitter() {}

    public static List<TeachingWeek> split(YearMonth month) {
        List<LocalDate[]> spans = new ArrayList<>();
        LocalDate day = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        while (!day.isAfter(last)) {
            LocalDate end = day.with(DayOfWeek.SUNDAY);
            if (end.isAfter(last)) end = last;
            spans.add(new LocalDate[] {day, end});
            day = end.plusDays(1);
        }
        if (spans.size() > 1 && !hasSchoolDay(spans.get(0))) {
            spans.get(1)[0] = spans.get(0)[0];
            spans.remove(0);
        }
        // A stub of Saturday/Sunday at the end of the month cannot exist: every later span starts on a Monday, so a
        // trailing Saturday or Sunday already sits in the previous week's span.
        List<TeachingWeek> weeks = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            LocalDate from = spans.get(i)[0];
            LocalDate to = spans.get(i)[1];
            LocalDate first = firstSchoolDay(from);
            LocalDate labelEnd = i == spans.size() - 1 ? last : first.with(DayOfWeek.FRIDAY);
            weeks.add(new TeachingWeek(i + 1, from, to, label(first, labelEnd)));
        }
        return List.copyOf(weeks);
    }

    private static boolean hasSchoolDay(LocalDate[] span) {
        return !span[0].isAfter(span[1]) && !firstSchoolDay(span[0]).isAfter(span[1]);
    }

    /** First Monday-Friday on or after {@code from}. */
    private static LocalDate firstSchoolDay(LocalDate from) {
        return switch (from.getDayOfWeek()) {
            case SATURDAY -> from.plusDays(2);
            case SUNDAY -> from.plusDays(1);
            default -> from;
        };
    }

    private static String label(LocalDate first, LocalDate end) {
        return first.equals(end) ? two(first) : two(first) + "-" + two(end);
    }

    private static String two(LocalDate d) {
        return String.format("%02d", d.getDayOfMonth());
    }
}
