package com.erg.ergspringboot.calendar;

import java.util.List;

/**
 * Inputs of editTeachingScheduleSlot and swapTeachingScheduleTeacher: changes to PUBLISHED periods. Dates are ISO
 * yyyy-MM-dd strings; null patch fields mean "unchanged".
 */
public final class TeachingScheduleSlotChanges {

    private TeachingScheduleSlotChanges() {
    }

    /** {@code kind}: THIS_ONLY, FROM_DATE_TO_END (this date to the last one of the batch) or SELECTED_DATES ({@code dates}). */
    public record Scope(String kind, List<String> dates) {}

    /** Move/re-room/re-subject a period; {@code newDate} only with THIS_ONLY; {@code removeAssistant} clears the assistant. */
    public record Edit(String eventId, Integer expectedVersion, Scope scope, Boolean dryRun, Integer periodNo, String newDate,
            String roomId, String subjectId, String assistantId, Boolean removeAssistant, String reason) {}

    /** Replace the main teacher; {@code reason}: TIME_CONFLICT, TEACHER_ON_LEAVE or REASSIGNMENT. */
    public record Swap(String eventId, Integer expectedVersion, Scope scope, String newTeacherId, String reason, String note,
            Boolean dryRun) {}
}
