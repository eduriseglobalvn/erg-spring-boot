package com.erg.ergspringboot.reporting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportingOperationsTest {

    @Test
    void rejectsAssignmentOutsideTenantBeforeEnqueueing() {
        AssignmentScopeLookup assignments = mock(AssignmentScopeLookup.class);
        ReportJobPort jobs = mock(ReportJobPort.class);
        when(assignments.findByTenantIdAndId("tenant-1", "assignment-1")).thenReturn(Optional.empty());

        ReportingOperations operations = new ReportingOperations(jobs, assignments);
        UserAccessContext access = new UserAccessContext("user-1", "tenant-1", java.util.List.of("*"),
                java.util.List.of("*"), null, null);

        assertThatThrownBy(() -> operations.enqueueAssignmentProgress(access, "assignment-1", "idem-1"))
                .isInstanceOf(com.erg.ergspringboot.exception.ForbiddenException.class);
    }

    @Test
    void jobStatusIsLimitedToRequesterOrAssignmentClassScope() {
        AssignmentScopeLookup assignments = mock(AssignmentScopeLookup.class);
        ReportJobPort jobs = mock(ReportJobPort.class);
        var job = new com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse(
                "job-1", "ASSIGNMENT_PROGRESS", "assignment-b", "completed", 100, null, null, null, null, "csv",
                null, "/api/v1/reports/jobs/job-1/download");
        when(jobs.get("tenant-1", "job-1")).thenReturn(job);
        when(jobs.requestedBy("tenant-1", "job-1")).thenReturn(Optional.of("teacher-b"));
        when(assignments.findByTenantIdAndId("tenant-1", "assignment-b"))
                .thenReturn(Optional.of(new AssignmentScopeLookup.AssignmentScope("tenant-1", "class-b")));
        ReportingOperations operations = new ReportingOperations(jobs, assignments);

        UserAccessContext teacherA = new UserAccessContext("teacher-a", "tenant-1", java.util.List.of("school-1"),
                java.util.List.of("class-a"), null, null);
        assertThatThrownBy(() -> operations.status(teacherA, "job-1"))
                .isInstanceOf(com.erg.ergspringboot.exception.ForbiddenException.class);

        UserAccessContext requester = new UserAccessContext("teacher-b", "tenant-1", java.util.List.of(),
                java.util.List.of(), null, null);
        org.assertj.core.api.Assertions.assertThat(operations.status(requester, "job-1").id()).isEqualTo("job-1");
        UserAccessContext classB = new UserAccessContext("lead-b", "tenant-1", java.util.List.of("school-1"),
                java.util.List.of("class-b"), null, null);
        org.assertj.core.api.Assertions.assertThat(operations.status(classB, "job-1").id()).isEqualTo("job-1");
    }
}
