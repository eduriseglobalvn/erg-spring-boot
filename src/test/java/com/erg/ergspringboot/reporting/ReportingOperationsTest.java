package com.erg.ergspringboot.reporting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.reporting.ReportJobService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportingOperationsTest {

    @Test
    void rejectsAssignmentOutsideTenantBeforeEnqueueing() {
        AssignmentScopeLookup assignments = mock(AssignmentScopeLookup.class);
        ReportJobService jobs = mock(ReportJobService.class);
        when(assignments.findByTenantIdAndId("tenant-1", "assignment-1")).thenReturn(Optional.empty());

        ReportingOperations operations = new ReportingOperations(jobs, assignments);
        UserAccessContext access = new UserAccessContext("user-1", "tenant-1", java.util.List.of("*"),
                java.util.List.of("*"), null, null);

        assertThatThrownBy(() -> operations.enqueueAssignmentProgress(access, "assignment-1", "idem-1"))
                .isInstanceOf(com.erg.ergspringboot.exception.ForbiddenException.class);
    }
}
