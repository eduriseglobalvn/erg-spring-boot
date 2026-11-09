package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.auth.internal.refresh.AuthRefreshUseCase;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AuthOperationsRefreshScopeTest {

    @Test
    void refreshRunsInTheSystemScopeAndRestoresTheCallerContext() {
        AuthRefreshUseCase refreshFlow = mock(AuthRefreshUseCase.class);
        AtomicReference<TenantContext> seen = new AtomicReference<>();
        when(refreshFlow.refresh(any(), any())).thenAnswer(call -> {
            seen.set(TenantContext.current());
            return null;
        });

        new AuthOperations(null, refreshFlow, null, null, null).refresh(null, null);

        assertThat(seen.get().isMaintenance()).isTrue();
        assertThat(TenantContext.current()).isNull();
    }
}
