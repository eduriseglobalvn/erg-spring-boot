package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.service.auth.AuthService;
import org.junit.jupiter.api.Test;

class AuthOperationsTest {
    @Test
    void delegatesLoginThroughTheAuthModuleBoundary() {
        AuthService legacy = mock(AuthService.class);
        LoginRequest request = mock(LoginRequest.class);
        AuthClientContext context = mock(AuthClientContext.class);
        AuthResponse expected = mock(AuthResponse.class);
        when(legacy.login(request, context)).thenReturn(expected);

        assertThat(new AuthOperations(legacy).login(request, context)).isSameAs(expected);
    }
}
