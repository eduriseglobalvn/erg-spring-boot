package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.reporting.AssignmentScopeLookup;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Lms implements reporting's port so reporting never depends on lms persistence (lms already depends on reporting via lcms).
@Component
class AssignmentScopeLookupAdapter implements AssignmentScopeLookup {
    private final AssignmentRepository assignments;

    AssignmentScopeLookupAdapter(AssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    public Optional<AssignmentScope> findByTenantIdAndId(String tenantId, String assignmentId) {
        return assignments.findByTenantIdAndId(tenantId, assignmentId)
                .map(this::toScope);
    }

    private AssignmentScope toScope(Assignment assignment) {
        return new AssignmentScope(assignment.getTenantId(), assignment.getAcademicClassId());
    }
}
