package com.erg.ergspringboot.reporting.internal;

import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.reporting.AssignmentScopeLookup;
import com.erg.ergspringboot.service.lms.LmsAssignmentService;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class AssignmentScopeLookupAdapter implements AssignmentScopeLookup {
    private final LmsAssignmentService assignments;

    AssignmentScopeLookupAdapter(LmsAssignmentService assignments) {
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
