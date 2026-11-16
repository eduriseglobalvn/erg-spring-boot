package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.erg.ergspringboot.common.request.users.UserRequests.BulkDeleteRequest;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.AccountActionGuard;
import com.erg.ergspringboot.platform.security.AccountActionGuard.AccountAction;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Account mutations reach the use cases only after the guard has accepted every target. */
class UserOperationsAccountGuardTest {

    private final UserLifecycleUseCase lifecycle = mock(UserLifecycleUseCase.class);
    private final UserProvisioningUseCase provisioning = mock(UserProvisioningUseCase.class);
    private final UserSessionUseCase sessions = mock(UserSessionUseCase.class);
    private final AccountActionGuard guard = mock(AccountActionGuard.class);
    private final UserOperations operations = new UserOperations(
            mock(UserQueryUseCase.class), mock(UserProfileUseCase.class), mock(UserPasswordUseCase.class),
            sessions, provisioning, lifecycle, mock(UserRoleAssignmentUseCase.class), guard);

    @Test
    void bulkDeleteRejectsWholeBatchWhenOneTargetIsAdmin() {
        doThrow(new ForbiddenException("denied")).when(guard)
                .assertCanActOnAll(any(), any(), org.mockito.ArgumentMatchers.eq(AccountAction.DELETE));

        assertThatThrownBy(() -> operations.bulkDelete("erg", new BulkDeleteRequest(List.of("teacher-1", "admin-1"))))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(lifecycle);
    }

    @Test
    void resetCredentialAndSessionRevokeAreGuarded() {
        operations.resetCredential("user-1", "erg", "ONE_TIME_PASSWORD", null);
        operations.resetCredential("user-1", "erg", "EMAIL_INVITE", null);
        operations.revokeSession("user-1", "erg", "session-1", null);

        verify(guard).assertCanActOn(null, "user-1", AccountAction.RESET_PASSWORD);
        verify(guard).assertCanActOn(null, "user-1", AccountAction.RESEND_INVITE);
        verify(guard).assertCanActOn(null, "user-1", AccountAction.REVOKE_SESSIONS);
    }
}
