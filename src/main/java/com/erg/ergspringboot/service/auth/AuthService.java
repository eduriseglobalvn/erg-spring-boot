package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.auth.internal.login.AuthLoginUseCase;
import com.erg.ergspringboot.auth.internal.refresh.AuthRefreshUseCase;
import com.erg.ergspringboot.auth.internal.security.AuthSecurityQueryUseCase;
import com.erg.ergspringboot.auth.internal.session.AuthSessionLifecycleUseCase;
import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.RefreshTokenHistoryRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Compatibility facade; authentication behavior and transactions are owned by internal use cases. */
@Service
public class AuthService {

    private final AuthLoginUseCase loginFlow;
    private final AuthRefreshUseCase refreshTokens;
    private final AuthSessionLifecycleUseCase sessionLifecycle;
    private final AuthSecurityQueryUseCase securityQueries;

    @Autowired
    public AuthService(
            AuthLoginUseCase loginFlow,
            AuthRefreshUseCase refreshTokens,
            AuthSessionLifecycleUseCase sessionLifecycle,
            AuthSecurityQueryUseCase securityQueries) {
        this.loginFlow = loginFlow;
        this.refreshTokens = refreshTokens;
        this.sessionLifecycle = sessionLifecycle;
        this.securityQueries = securityQueries;
    }

    /** Compatibility constructor retained for legacy tests and callers. */
    public AuthService(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens) {
        this(new AuthLoginUseCase(users, sessions, attempts, passwords, tokens),
                new AuthRefreshUseCase(sessions, tokens),
                new AuthSessionLifecycleUseCase(sessions),
                new AuthSecurityQueryUseCase(attempts));
    }

    @Autowired(required = false)
    void setSecurityEventRepository(SecurityEventRepository repository) {
        loginFlow.setSecurityEventRepository(repository);
        refreshTokens.setSecurityEventRepository(repository);
    }

    @Autowired(required = false)
    void setLoginFailureEnforcementService(LoginFailureEnforcementService service) {
        loginFlow.setLoginFailureEnforcementService(service);
    }

    @Autowired(required = false)
    void setAuthLoginRateLimiter(AuthLoginRateLimiter limiter) {
        loginFlow.setAuthLoginRateLimiter(limiter);
    }

    @Autowired(required = false)
    void setUserRoleAssignmentRepository(UserRoleAssignmentRepository repository) {
        loginFlow.setUserRoleAssignmentRepository(repository);
    }

    @Autowired(required = false)
    void setRefreshTokenHistoryRepository(RefreshTokenHistoryRepository repository) {
        refreshTokens.setRefreshTokenHistoryRepository(repository);
    }

    public AuthResponse login(LoginRequest request, AuthClientContext context) {
        return loginFlow.login(request, context);
    }

    public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
        return refreshTokens.refresh(request, context);
    }

    public void logout(String userId, String tenantId, String sessionId) {
        sessionLifecycle.logout(userId, tenantId, sessionId);
    }

    public int logoutAll(String userId, String tenantId) {
        return sessionLifecycle.logoutAll(userId, tenantId);
    }

    public List<LoginAttemptResponse> listLoginAttempts(String userId, String email) {
        return securityQueries.loginAttempts(userId, email);
    }

    public IpSecurityStatusResponse ipSecurityStatus(String email, AuthClientContext context) {
        return securityQueries.ipStatus(email, context);
    }
}
