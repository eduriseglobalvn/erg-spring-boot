package com.erg.ergspringboot.lcms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.lms.LmsOperationalResponses;
import com.erg.ergspringboot.controller.lcms.graphql.LcmsSchoolOperationsMutationGraphQlController;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.LmsOperationalOperations;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.AuthenticationRequiredException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LcmsSchoolOperationsMutationGraphQlControllerTest {

    @Mock private LmsOperationalOperations operations;
    @Mock private GraphQlAccessSupport accessSupport;

    private LcmsSchoolOperationsMutationGraphQlController controller;
    private final ErgPrincipal principal = ErgPrincipal.authenticated(
            "actor-1", "session-1", "tenant-1", List.of("admin"),
            List.of("curriculum.manage"), List.of(), List.of("lcms"),
            "user", "admin", Map.of());

    @BeforeEach
    void setUp() {
        controller = new LcmsSchoolOperationsMutationGraphQlController(operations, accessSupport);
    }

    @Test
    void subjectMutationKeepsTheFullTypedPermissionAliasSet() {
        when(accessSupport.currentPrincipal()).thenReturn(principal);
        when(accessSupport.verifiedTenantId(principal, "tenant-1")).thenReturn("tenant-1");
        when(operations.createSubject(any(), any())).thenReturn(new LmsOperationalResponses.SubjectResponse(
                "subject-1", "tenant-1", null, "Mathematics", "MATH", "ACTIVE", 0L, null));

        var response = controller.createSchoolSubject(
                new LcmsSchoolOperationsMutationGraphQlController.CreateSchoolSubjectInput(
                        "tenant-1", null, "Mathematics", "MATH", "ACTIVE"));

        assertThat(response.id()).isEqualTo("subject-1");
        verify(accessSupport).assertPermission(
                principal, "lms.subject.manage", "curriculum.manage", "lms.class.manage", "*");
    }

    @Test
    void missingPrincipalFailsClosedAsAuthenticationRequired() {
        when(accessSupport.currentPrincipal()).thenReturn(null);

        assertThatThrownBy(() -> controller.createSchoolSubject(
                new LcmsSchoolOperationsMutationGraphQlController.CreateSchoolSubjectInput(
                        "tenant-1", null, "Mathematics", "MATH", "ACTIVE")))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void negativeGraphQlVersionIsRejectedBeforeCallingTheFacade() {
        when(accessSupport.currentPrincipal()).thenReturn(principal);
        when(accessSupport.verifiedTenantId(principal, "tenant-1")).thenReturn("tenant-1");

        assertThatThrownBy(() -> controller.updateSchoolSubject(
                new LcmsSchoolOperationsMutationGraphQlController.UpdateSchoolSubjectInput(
                        "tenant-1", "subject-1", "-1", "Mathematics", "MATH", "ACTIVE")))
                .isInstanceOf(BadRequestException.class);
    }
}
