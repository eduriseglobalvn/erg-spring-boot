package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipient;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * A student who joins a class receives that class's open assignments given to the whole class (BUG-X-001,
 * owner decision 17/11/2026). Past-due, non-open and hand-picked/group assignments are left alone. Idempotent.
 */
@Service
public class ClassAssignmentBackfill {

    private static final Set<String> OPEN_STATUSES = Set.of("active", "assigned", "published");

    private final AssignmentRepository assignments;
    private final AssignmentRecipientRepository recipients;
    private final LmsReadCacheInvalidator caches;

    public ClassAssignmentBackfill(AssignmentRepository assignments, AssignmentRecipientRepository recipients,
            LmsReadCacheInvalidator caches) {
        this.assignments = assignments;
        this.recipients = recipients;
        this.caches = caches;
    }

    /** Call after the student's {@code academicClassId} is set; the caller's transaction covers the writes. */
    public int grantOpenClassAssignments(Student student) {
        String tenantId = student.getTenantId();
        String classId = student.getAcademicClassId();
        if (tenantId == null || classId == null || student.getId() == null) return 0;
        OffsetDateTime now = OffsetDateTime.now();
        List<Assignment> granted = new ArrayList<>();
        for (Assignment assignment : assignments.findByTenantIdAndAcademicClassIdOrderByCreatedAtDesc(tenantId, classId)) {
            if (!"class".equalsIgnoreCase(assignment.getRecipientMode())) continue;
            if (assignment.getStatus() == null || !OPEN_STATUSES.contains(assignment.getStatus().toLowerCase(Locale.ROOT))) continue;
            if (assignment.getDueAt() != null && !assignment.getDueAt().isAfter(now)) continue;
            if (recipients.findFirstByTenantIdAndAssignmentIdAndStudentId(tenantId, assignment.getId(), student.getId()).isPresent()) continue;
            AssignmentRecipient row = new AssignmentRecipient();
            row.setAssignmentId(assignment.getId());
            row.setTenantId(tenantId);
            row.setStudentId(student.getId());
            row.setStudentUserId(student.getAuthUserId());
            row.setClassId(classId);
            row.setSourceMode("class");
            recipients.save(row);
            // Keep the denormalised snapshot (legacy read path) and the count in step with the recipient table.
            List<String> ids = new ArrayList<>(assignment.getStudentIds() == null ? List.of() : assignment.getStudentIds());
            if (!ids.contains(student.getId())) ids.add(student.getId());
            assignment.setStudentIds(ids);
            assignment.setRecipientCount((int) recipients.countByAssignmentId(assignment.getId()));
            granted.add(assignment);
        }
        if (granted.isEmpty()) return 0;
        assignments.saveAll(granted);
        caches.evictAfterCommit(tenantId, LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES);
        return granted.size();
    }
}
