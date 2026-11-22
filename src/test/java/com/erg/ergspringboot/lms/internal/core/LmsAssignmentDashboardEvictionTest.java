package com.erg.ergspringboot.lms.internal.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentResourceRepository;
import com.erg.ergspringboot.lms.persistence.StudentGroupMemberRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class LmsAssignmentDashboardEvictionTest {
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final AssignmentRecipientRepository recipients = mock(AssignmentRecipientRepository.class);
    private final AssignmentRecipientLogins logins = mock(AssignmentRecipientLogins.class);
    private final LmsReadCacheInvalidator caches = mock(LmsReadCacheInvalidator.class);
    private final LmsAssignmentCommandService service = new LmsAssignmentCommandService(assignments,
            mock(AssignmentResourceRepository.class), recipients, mock(StudentGroupMemberRepository.class),
            mock(StudentRepository.class), mock(QuizVersionRepository.class), caches, mock(LmsAssignmentAccessPolicy.class),
            (com.erg.ergspringboot.platform.sync.LmsSyncOperationService) null, event -> { });
    private final UserAccessContext admin = new UserAccessContext("teacher-1", "tenant-a", List.of("*"), List.of("*"), null, null);

    LmsAssignmentDashboardEvictionTest() {
        Assignment assignment = new Assignment();
        assignment.setId("as-1");
        assignment.setTenantId("tenant-a");
        when(assignments.findById("as-1")).thenReturn(Optional.of(assignment));
        service.setRecipientLogins(logins);
    }

    @Test
    void deletingAnAssignmentDropsOnlyItsRecipientsDashboards() {
        when(logins.findLogins(anyString(), any(Pageable.class))).thenReturn(List.of("u1", "u2"));

        service.delete(admin, "as-1");

        verify(caches).evictScopesAfterCommit("tenant-a", Map.of("studentDashboard", List.of("u1", "u2")));
        verify(caches).evictAfterCommit(anyString(), argThat(names -> !names.contains("studentDashboard") && names.contains("lmsStudentAssignments")));
    }

    @Test
    void anAssignmentWithoutRecipientRowsFallsBackToTheWholeTenantDashboards() {
        when(logins.findLogins(anyString(), any(Pageable.class))).thenReturn(List.of());
        when(recipients.existsByTenantIdAndAssignmentId("tenant-a", "as-1")).thenReturn(false);

        service.delete(admin, "as-1");

        verify(caches).evictAfterCommit("tenant-a", LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES);
        verify(caches, never()).evictScopesAfterCommit(anyString(), anyMapOf());
    }

    @Test
    void aVeryLargeAudienceFallsBackToTheWholeTenantDashboards() {
        when(logins.findLogins(anyString(), any(Pageable.class)))
                .thenReturn(IntStream.range(0, 201).mapToObj(i -> "u" + i).toList());

        service.delete(admin, "as-1");

        verify(caches).evictAfterCommit("tenant-a", LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES);
    }

    private static Map<String, List<String>> anyMapOf() {
        return any();
    }
}
