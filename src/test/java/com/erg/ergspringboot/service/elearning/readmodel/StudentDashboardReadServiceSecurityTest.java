package com.erg.ergspringboot.service.elearning.readmodel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentDashboardReadServiceSecurityTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ErgPolicyService policyService;

    @Test
    void studentDashboardRejectsTenantSpoofingBeforeRepositoryRead() {
        when(policyService.isAllowed(any(), any(), any(), any())).thenReturn(true);
        StudentDashboardReadService service = service();

        assertThatThrownBy(() -> service.studentDashboard(scope(
                "student-1",
                "tenant-a",
                "tenant-b",
                "student-1",
                List.of("elearning.assignment.read_self")
        ))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(assignmentRepository, attemptRepository, studentRepository, userRepository);
    }

    @Test
    void studentDashboardRejectsWrongPermissionBeforeRepositoryRead() {
        when(policyService.isAllowed(any(), any(), any(), any())).thenReturn(false);
        StudentDashboardReadService service = service();

        assertThatThrownBy(() -> service.studentDashboard(scope(
                "student-1",
                "tenant-a",
                null,
                "student-1",
                List.of("lms.class.read")
        ))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(assignmentRepository, attemptRepository, studentRepository, userRepository);
    }

    @Test
    void studentDashboardRejectsOtherStudentUserIdBeforeRepositoryRead() {
        when(policyService.isAllowed(any(), any(), any(), any())).thenReturn(true);
        StudentDashboardReadService service = service();

        assertThatThrownBy(() -> service.studentDashboard(scope(
                "student-1",
                "tenant-a",
                null,
                "student-2",
                List.of("elearning.assignment.read_self")
        ))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(assignmentRepository, attemptRepository, studentRepository, userRepository);
    }

    private StudentDashboardReadService service() {
        return new StudentDashboardReadService(
                assignmentRepository,
                attemptRepository,
                studentRepository,
                userRepository,
                policyService
        );
    }

    private StudentDashboardReadScope scope(
            String userId,
            String tenantId,
            String requestedTenantId,
            String requestedStudentUserId,
            List<String> permissions
    ) {
        return new StudentDashboardReadScope(
                userId,
                tenantId,
                requestedTenantId,
                requestedStudentUserId,
                List.of("elearning.learner"),
                permissions,
                List.of(),
                List.of("elearning"),
                "student",
                "self"
        );
    }
}
