package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import org.springframework.stereotype.Service;

/** Assignment progress projection boundary. */
@Service
public class LmsAssignmentProgressQueryUseCase {
    private final AttemptRepository attempts;
    public LmsAssignmentProgressQueryUseCase(AttemptRepository attempts) { this.attempts = attempts; }
    public AssignmentProgressProjection summary(String tenantId, String assignmentId) {
        return attempts.summarizeAssignmentProgress(tenantId, assignmentId);
    }
}
