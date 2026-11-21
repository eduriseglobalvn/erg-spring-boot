package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import org.springframework.stereotype.Service;

/**
 * A student who joins a class receives that class's open assignments given to the whole class (BUG-X-001,
 * owner decision 17/11/2026). Past-due, non-open and hand-picked/group assignments are left alone. Idempotent.
 * Only the recipient rows are written (one set-based statement, no student_ids read-modify-write): every read path
 * resolves recipients from assignment_recipients first.
 */
@Service
public class ClassAssignmentBackfill {

    private final AssignmentRecipientRepository recipients;
    private final LmsReadCacheInvalidator caches;

    public ClassAssignmentBackfill(AssignmentRecipientRepository recipients, LmsReadCacheInvalidator caches) {
        this.recipients = recipients;
        this.caches = caches;
    }

    /** Call after the student's {@code academicClassId} is set; the caller's transaction covers the writes. */
    public int grantOpenClassAssignments(Student student) {
        String tenantId = student.getTenantId();
        String classId = student.getAcademicClassId();
        if (tenantId == null || classId == null || student.getId() == null) return 0;
        int granted = recipients.grantOpenClassAssignments(tenantId, classId, student.getId(), student.getAuthUserId());
        if (granted > 0) {
            // The class workspace of the joined class is dropped by class; every other class's workspace is untouched.
            caches.evictAfterCommit(tenantId, LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES.stream()
                    .filter(name -> !"lmsClassWorkspace".equals(name)).toList());
            caches.evictClassWorkspaces(tenantId, classId);
        }
        return granted;
    }
}
