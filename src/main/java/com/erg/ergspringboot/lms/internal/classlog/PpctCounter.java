package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.lms.LessonScheduleReadPort;
import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * PPCT (curriculum-plan position) of a timetable period: how many published, non-cancelled periods of the same class
 * and subject lie from the plan start date up to and including that period. A two-period event gives n and n + 1.
 */
@Component
public class PpctCounter {

    /** Identifies one period of one event; an event lasts one day, so the date is implied. */
    public record PeriodKey(String eventId, int periodNo) {}

    private final LessonScheduleReadPort schedule;

    PpctCounter(LessonScheduleReadPort schedule) {
        this.schedule = schedule;
    }

    /**
     * One query for the whole range; every period of every slot from {@code planStart} to {@code to} is numbered.
     * The plan start date is per (school, subject, year), so callers group by it and call once per start date.
     */
    public Map<PeriodKey, Integer> positions(
            String tenantId, Collection<String> classIds, Collection<String> subjectIds, LocalDate planStart, LocalDate to) {
        return number(schedule.publishedSlots(tenantId, classIds, subjectIds, planStart, to));
    }

    static Map<PeriodKey, Integer> number(List<LessonSlot> slots) {
        Map<String, List<LessonSlot>> perClassSubject = new HashMap<>();
        for (LessonSlot slot : slots) {
            perClassSubject.computeIfAbsent(slot.classId() + '\n' + slot.subjectId(), k -> new ArrayList<>()).add(slot);
        }
        Map<PeriodKey, Integer> result = new HashMap<>();
        Comparator<LessonSlot> byTime = Comparator.comparing(LessonSlot::date).thenComparingInt(LessonSlot::periodStart);
        for (List<LessonSlot> group : perClassSubject.values()) {
            group.sort(byTime);
            int position = 0;
            for (LessonSlot slot : group) {
                for (int k = 0; k < slot.periodCount(); k++) {
                    result.put(new PeriodKey(slot.eventId(), slot.periodStart() + k), ++position);
                }
            }
        }
        return result;
    }
}
