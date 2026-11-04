package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.auth.internal.login.AuthLoginUseCase;
import org.junit.jupiter.api.Test;

class AuthOperationsTest {
    @Test
    void delegatesLoginThroughTheAuthModuleBoundary() {
        AuthLoginUseCase loginFlow = mock(AuthLoginUseCase.class);
        LoginRequest request = mock(LoginRequest.class);
        AuthClientContext context = mock(AuthClientContext.class);
        AuthResponse expected = mock(AuthResponse.class);
        when(loginFlow.login(request, context)).thenReturn(expected);

        assertThat(new AuthOperations(loginFlow, null, null, null, null).login(request, context)).isSameAs(expected);
    }
}
