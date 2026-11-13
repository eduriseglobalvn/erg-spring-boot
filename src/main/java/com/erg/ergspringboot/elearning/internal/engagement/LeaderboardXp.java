package com.erg.ergspringboot.elearning.internal.engagement;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * XP rules of the "Thi đua" board (design H03): only XP ranks students, never their scores.
 * A handed-in assignment earns 10 + 1.5 per question (at most 40), +10 when handed in before the deadline; an
 * assignment counts once, on its first hand-in. A streak of 7+ days multiplies the period's XP by 1.2.
 */
public final class LeaderboardXp {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int STREAK_BONUS_DAYS = 7;

    /** COURSE: the school year, 1 Sep - 31 Aug (the class has no course entity yet). */
    public enum Period { WEEK, MONTH, TERM, COURSE }

    enum Scope { CLASS, GRADE }

    /** {@code [start, end)} in Vietnam time. */
    record Window(ZonedDateTime start, ZonedDateTime end) {
        Window previous(Period period) {
            return window(period, start.minusDays(1));
        }
    }

    private LeaderboardXp() {
    }

    static Period period(String value) {
        if (value == null || value.isBlank()) return Period.WEEK;
        return Period.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    static Scope scope(String value) {
        if (value == null || value.isBlank()) return Scope.CLASS;
        return Scope.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    /** Week from Monday; term I is 1 Sep – 15 Jan, term II 16 Jan – 31 Aug. */
    static Window window(Period period, ZonedDateTime at) {
        LocalDate day = at.withZoneSameInstant(ZONE).toLocalDate();
        LocalDate start;
        LocalDate end;
        switch (period) {
            case WEEK -> {
                start = day.with(DayOfWeek.MONDAY);
                end = start.plusWeeks(1);
            }
            case COURSE -> {
                start = LocalDate.of(day.getMonthValue() >= 9 ? day.getYear() : day.getYear() - 1, 9, 1);
                end = start.plusYears(1);
            }
            case MONTH -> {
                start = day.withDayOfMonth(1);
                end = start.plusMonths(1);
            }
            default -> {
                LocalDate termOneStart = LocalDate.of(day.getMonth().compareTo(Month.SEPTEMBER) >= 0 ? day.getYear() : day.getYear() - 1, 9, 1);
                LocalDate termTwoStart = LocalDate.of(termOneStart.getYear() + 1, 1, 16);
                boolean termOne = day.isBefore(termTwoStart);
                start = termOne ? termOneStart : termTwoStart;
                end = termOne ? termTwoStart : LocalDate.of(termTwoStart.getYear(), 9, 1);
            }
        }
        return new Window(start.atStartOfDay(ZONE), end.atStartOfDay(ZONE));
    }

    public static int handInXp(Integer totalQuestions, boolean beforeDeadline) {
        int questions = totalQuestions == null ? 0 : Math.max(0, totalQuestions);
        return Math.min(40, 10 + (int) Math.round(questions * 1.5)) + (beforeDeadline ? 10 : 0);
    }

    /** Consecutive days with a hand-in, ending today or (not yet done today) yesterday. */
    static int streak(Set<LocalDate> days, LocalDate today) {
        LocalDate day = days.contains(today) ? today : today.minusDays(1);
        int count = 0;
        while (days.contains(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    /** School year (start year) a day belongs to: 1 Sep - 31 Aug. */
    public static int schoolYearStart(LocalDate day) {
        return day.getMonthValue() >= 9 ? day.getYear() : day.getYear() - 1;
    }

    /**
     * XP of a whole course: the sum of each week's XP, where a week earns the streak bonus when the student has a
     * 7+ day streak ending on that week's Sunday (or on {@code today} for the running week).
     */
    static int courseXp(Map<LocalDate, Integer> xpByWeekStart, Set<LocalDate> activeDays, LocalDate today) {
        int total = 0;
        for (Map.Entry<LocalDate, Integer> week : xpByWeekStart.entrySet()) {
            LocalDate sunday = week.getKey().plusDays(6);
            total += withStreakBonus(week.getValue(), streak(activeDays, sunday.isAfter(today) ? today : sunday));
        }
        return total;
    }

    static int withStreakBonus(int xp, int streak) {
        return streak >= STREAK_BONUS_DAYS ? (int) Math.round(xp * 1.2) : xp;
    }
}
