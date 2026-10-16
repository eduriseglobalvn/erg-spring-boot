package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.lms.LmsAssignmentQueryPort;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.projection.AssignmentCardProjection;
import com.erg.ergspringboot.repository.lms.projection.AssignmentDetailProjection;
import com.erg.ergspringboot.repository.lms.projection.MissingLateStudentProjection;
import com.erg.ergspringboot.repository.lms.projection.RecipientProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.TeacherHomeworkSummaryProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Current JPA adapter for the assignment query port. */
@Service
public class JpaLmsAssignmentQueryAdapter implements LmsAssignmentQueryPort {
    private final AssignmentRepository assignments;

    public JpaLmsAssignmentQueryAdapter(AssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override public Optional<AssignmentDetailProjection> findAssignmentDetail(String tenantId, String assignmentId) { return assignments.findAssignmentDetail(tenantId, assignmentId); }
    @Override public Page<RecipientProgressProjection> findAssignmentRecipients(String tenantId, String assignmentId, Pageable pageable) { return assignments.findAssignmentRecipients(tenantId, assignmentId, pageable); }
    @Override public Page<MissingLateStudentProjection> findAssignmentMissingLateStudents(String tenantId, String assignmentId, Pageable pageable) { return assignments.findAssignmentMissingLateStudents(tenantId, assignmentId, pageable); }
    @Override public Page<AssignmentCardProjection> findTeacherAssignmentCards(String tenantId, String teacherId, String status, Pageable pageable) { return assignments.findTeacherAssignmentCards(tenantId, teacherId, status, pageable); }
    @Override public TeacherHomeworkSummaryProjection summarizeTeacherHomeworkWorkspace(String tenantId, String teacherId) { return assignments.summarizeTeacherHomeworkWorkspace(tenantId, teacherId); }
}
