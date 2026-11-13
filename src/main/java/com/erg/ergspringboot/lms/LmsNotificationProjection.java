package com.erg.ergspringboot.lms;

import java.util.List;
import java.util.Optional;

/** Public, persistence-free audience lookup for notifications triggered by LMS events. */
public interface LmsNotificationProjection {

    Optional<AssignmentAudience> assignmentAudience(String tenantId, String assignmentId);

    Optional<Submitter> submitter(String tenantId, String studentId);

    /** {@code studentUserIds} holds only recipients linked to a login account. */
    record AssignmentAudience(
            String assignmentId,
            String title,
            String classId,
            String assignedBy,
            List<String> studentUserIds
    ) {
        public AssignmentAudience {
            studentUserIds = studentUserIds == null ? List.of() : List.copyOf(studentUserIds);
        }
    }

    /** {@code userId} is the student's login account ({@code users.id}); null when none is linked. */
    record Submitter(String fullName, String userId) {
    }
}
