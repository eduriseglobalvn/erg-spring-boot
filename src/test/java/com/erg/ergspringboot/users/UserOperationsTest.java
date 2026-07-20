package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.service.users.UserService;
import org.junit.jupiter.api.Test;

class UserOperationsTest {
    @Test
    void exposesCurrentUserThroughCompatibilityFacade() {
        UserService legacy = mock(UserService.class);
        UserResponse expected = mock(UserResponse.class);
        when(legacy.getCurrentUser("user-1", "tenant-1")).thenReturn(expected);

        assertThat(new UserOperations(legacy).current("user-1", "tenant-1")).isSameAs(expected);
    }
}
