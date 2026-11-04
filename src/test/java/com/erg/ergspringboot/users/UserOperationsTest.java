package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;
import org.junit.jupiter.api.Test;

class UserOperationsTest {
    @Test
    void exposesCurrentUserThroughQueryUseCase() {
        UserQueryUseCase queries = mock(UserQueryUseCase.class);
        UserResponse expected = mock(UserResponse.class);
        when(queries.current("user-1", "tenant-1")).thenReturn(expected);

        UserOperations operations = new UserOperations(queries,
                mock(UserProfileUseCase.class), mock(UserPasswordUseCase.class),
                mock(UserSessionUseCase.class), mock(UserProvisioningUseCase.class),
                mock(UserLifecycleUseCase.class), mock(UserRoleAssignmentUseCase.class));

        assertThat(operations.current("user-1", "tenant-1")).isSameAs(expected);
    }
}
