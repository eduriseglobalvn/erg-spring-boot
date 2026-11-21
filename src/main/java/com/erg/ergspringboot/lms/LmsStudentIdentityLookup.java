package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
import com.erg.ergspringboot.lms.persistence.Student;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * The one lookup of "which student is this login" on the learner hot paths (autosave, package, scores, dashboards).
 * Cached 60 s per tenant and login ({@code lmsStudentIdentity}), "no student" included. Every writer of lms_students that
 * changes class, school, status, name or the auth link must call {@code LmsReadCacheInvalidator.evictStudentIdentity}.
 */
@Component
public class LmsStudentIdentityLookup {
    /** Small cacheable snapshot; {@code studentId == null} means the login has no student row. */
    public record StudentIdentity(String studentId, String authUserId, String academicClassId, String schoolId,
            String fullName, String status) {
        public static final StudentIdentity NONE = new StudentIdentity(null, null, null, null, null, null);

        public boolean exists() {
            return studentId != null;
        }

        /** A detached, read-only Student view for the policy methods that take one; never persist it. */
        public Student toStudent(String tenantId) {
            Student student = new Student();
            student.setId(studentId);
            student.setTenantId(tenantId);
            student.setAuthUserId(authUserId);
            student.setAcademicClassId(academicClassId);
            student.setSchoolId(schoolId);
            student.setFullName(fullName);
            student.setStatus(status);
            return student;
        }
    }

    private final LmsStudentService students;

    public LmsStudentIdentityLookup(LmsStudentService students) {
        this.students = students;
    }

    @Cacheable(cacheNames = "lmsStudentIdentity", key = "{#tenantId, #authUserId}", sync = true,
            condition = "#authUserId != null and !#authUserId.isBlank()")
    public StudentIdentity byAuthUser(String tenantId, String authUserId) {
        if (authUserId == null || authUserId.isBlank()) return StudentIdentity.NONE;
        return students.findFirstByTenantIdAndAuthUserId(tenantId, authUserId)
                .map(s -> new StudentIdentity(s.getId(), s.getAuthUserId(), s.getAcademicClassId(), s.getSchoolId(),
                        s.getFullName(), s.getStatus()))
                .orElse(StudentIdentity.NONE);
    }
}
