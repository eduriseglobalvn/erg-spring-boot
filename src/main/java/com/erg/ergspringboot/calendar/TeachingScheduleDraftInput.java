package com.erg.ergspringboot.calendar;

import java.util.List;

/** Input of saveTeachingScheduleDraft (KE_HOACH_LICH_DAY.md 4.2); dates are ISO yyyy-MM-dd strings. */
public record TeachingScheduleDraftInput(String batchId, String schoolId, String subjectId, String levelId,
        String applyFrom, String repeatUntil, String periodTemplateId, Integer expectedVersion, List<Row> rows,
        String idempotencyKey) {

    /** Without a client idempotency key (only used when batchId is null: a retry returns the batch the first save created). */
    public TeachingScheduleDraftInput(String batchId, String schoolId, String subjectId, String levelId,
            String applyFrom, String repeatUntil, String periodTemplateId, Integer expectedVersion, List<Row> rows) {
        this(batchId, schoolId, subjectId, levelId, applyFrom, repeatUntil, periodTemplateId, expectedVersion, rows, null);
    }

    public record Row(String clientRowId, String eventId, int weekday, int periodNo, String classId, String roomId,
            String newRoomName, String teacherId, String assistantId, String note) {}
}
