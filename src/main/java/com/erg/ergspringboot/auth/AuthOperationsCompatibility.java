package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.auth.internal.login.AuthLoginUseCase;
import com.erg.ergspringboot.auth.internal.refresh.AuthRefreshUseCase;
import com.erg.ergspringboot.auth.internal.security.AuthSecurityQueryUseCase;
import com.erg.ergspringboot.auth.internal.session.AuthSessionLifecycleUseCase;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.service.auth.AuthService;
import com.erg.ergspringboot.service.auth.AuthTokenService;
import com.erg.ergspringboot.service.auth.PasswordService;

/**
 * Temporary construction seam for legacy unit tests and non-Spring adapters.
 * Production wiring injects the module use cases directly into {@link AuthOperations}.
 */
@Deprecated
public final class AuthOperationsCompatibility {
    private AuthOperationsCompatibility() { }

    public static AuthOperations fromRepositories(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens) {
        return new AuthOperations(
                new AuthLoginUseCase(users, sessions, attempts, passwords, tokens),
                new AuthRefreshUseCase(sessions, tokens),
                new AuthSessionLifecycleUseCase(sessions),
                new AuthSecurityQueryUseCase(attempts),
                null);
    }
}
