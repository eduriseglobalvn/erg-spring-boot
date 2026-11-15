package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Scope;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Audit row of a slot edit or teacher swap: who, scope, reason and the first affected period before and after. */
@Component
class ScheduleSlotChangeAudit {

    private final AuditOperations audit;

    ScheduleSlotChangeAudit(AuditOperations audit) {
        this.audit = audit;
    }

    void record(String tenantId, String actorId, String kind, String reason, String note, Scope scope, CalendarBatch batch,
            List<String> eventIds, Occurrence before, Occurrence after, String changeId, OffsetDateTime now) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", scope.kind());
        if (scope.dates() != null) details.put("dates", scope.dates());
        details.put("reason", reason);
        details.put("note", note);
        details.put("batchId", batch.getId());
        details.put("affectedEventIds", eventIds);
        details.put("before", describe(before));
        details.put("after", describe(after));
        audit.record(new AuditRecord(tenantId, actorId, "EDUCATION_UNIT", batch.getSchoolId(),
                "EDIT".equals(kind) ? "SCHEDULE_SLOT_EDITED" : "SCHEDULE_TEACHER_SWAPPED", "TEACHING_SCHEDULE_EVENT",
                eventIds.get(0), AuditOutcome.SUCCESS, null, null, null, details, "schedule-slot-change:" + changeId, now, null));
    }

    private static Map<String, Object> describe(Occurrence o) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("date", o.date().toString());
        view.put("periodNo", o.periodNo());
        view.put("roomId", o.roomId());
        view.put("subject", o.subjectName());
        view.put("teachers", o.teachers().stream().map(t -> t.role() + ":" + t.id()).toList());
        return view;
    }
}
