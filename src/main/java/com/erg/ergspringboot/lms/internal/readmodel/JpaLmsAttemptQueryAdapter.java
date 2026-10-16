package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.lms.LmsAttemptQueryPort;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptScoreProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.PendingReviewProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Current JPA adapter for the attempt query port. */
@Service
public class JpaLmsAttemptQueryAdapter implements LmsAttemptQueryPort {
    private final AttemptRepository attempts;

    public JpaLmsAttemptQueryAdapter(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Override public AssignmentProgressProjection summarizeAssignmentProgress(String tenantId, String assignmentId) { return attempts.summarizeAssignmentProgress(tenantId, assignmentId); }
    @Override public Page<AttemptSummaryProjection> findAssignmentAttemptSummaries(String tenantId, String assignmentId, String status, Pageable pageable) { return attempts.findAssignmentAttemptSummaries(tenantId, assignmentId, status, pageable); }
    @Override public List<AttemptScoreProjection> findLatestAttemptScoresForRecipients(String tenantId, String assignmentId, Collection<String> studentIds) { return attempts.findLatestAttemptScoresForRecipients(tenantId, assignmentId, studentIds); }
    @Override public List<AttemptScoreProjection> findBestAttemptScoresForRecipients(String tenantId, String assignmentId, Collection<String> studentIds) { return attempts.findBestAttemptScoresForRecipients(tenantId, assignmentId, studentIds); }
    @Override public Page<PendingReviewProjection> findTeacherPendingReviews(String tenantId, String teacherId, Pageable pageable) { return attempts.findTeacherPendingReviews(tenantId, teacherId, pageable); }
}
