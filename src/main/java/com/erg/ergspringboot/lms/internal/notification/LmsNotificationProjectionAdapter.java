package com.erg.ergspringboot.lms.internal.notification;

import com.erg.ergspringboot.lms.LmsNotificationProjection;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipient;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class LmsNotificationProjectionAdapter implements LmsNotificationProjection {

    private final AssignmentRepository assignments;
    private final AssignmentRecipientRepository recipients;
    private final StudentRepository students;

    LmsNotificationProjectionAdapter(
            AssignmentRepository assignments,
            AssignmentRecipientRepository recipients,
            StudentRepository students
    ) {
        this.assignments = assignments;
        this.recipients = recipients;
        this.students = students;
    }

    @Override
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public Optional<AssignmentAudience> assignmentAudience(String tenantId, String assignmentId) {
        return assignments.findByTenantIdAndId(tenantId, assignmentId).map(assignment -> new AssignmentAudience(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getAcademicClassId(),
                assignment.getAssignedBy(),
                recipients.findByAssignmentId(assignmentId).stream()
                        .filter(recipient -> tenantId.equals(recipient.getTenantId()))
                        .map(AssignmentRecipient::getStudentUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()));
    }

    @Override
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public Optional<Submitter> submitter(String tenantId, String studentId) {
        return students.findByTenantIdAndId(tenantId, studentId)
                .map(student -> new Submitter(student.getFullName(), student.getAuthUserId()));
    }
}
