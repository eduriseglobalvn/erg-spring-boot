package com.erg.ergspringboot.calendar;

import java.util.List;

/**
 * Input of previewTeachingScheduleReuse / reuseTeachingScheduleWeek (KE_HOACH_LICH_DAY.md 4.2); dates are ISO strings.
 * {@code mode} is SELECTED_WEEKS (use {@code weekStarts}) or REPEAT_UNTIL (every week after the source up to {@code repeatUntil}).
 */
public record TeachingScheduleReuseInput(String sourceSchoolId, String sourceWeekStart, String mode, List<String> weekStarts,
        String repeatUntil, boolean skipHolidays, boolean keepRoomAndAssistant) {}
