package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.auth.AuthOperations;
import com.erg.ergspringboot.auth.AuthOperationsCompatibility;
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

/**
 * @deprecated Use {@link AuthOperations}. This bean is a compatibility adapter only;
 * the auth module owns use-case wiring and behavior.
 */
@Deprecated
@Service
public class AuthService {
    private final AuthOperations operations;

    @Autowired
    public AuthService(AuthOperations operations) { this.operations = operations; }

    /** Compatibility constructor retained for legacy tests and callers. */
    public AuthService(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens) {
        this(AuthOperationsCompatibility.fromRepositories(users, sessions, attempts, passwords, tokens));
    }

    @Autowired(required = false)
    void setSecurityEventRepository(SecurityEventRepository repository) {
        operations.setSecurityEventRepository(repository);
    }

    @Autowired(required = false)
    void setLoginFailureEnforcementService(LoginFailureEnforcementService service) {
        operations.setLoginFailureEnforcementService(service);
    }

    @Autowired(required = false)
    void setAuthLoginRateLimiter(AuthLoginRateLimiter limiter) {
        operations.setAuthLoginRateLimiter(limiter);
    }

    @Autowired(required = false)
    void setUserRoleAssignmentRepository(UserRoleAssignmentRepository repository) {
        operations.setUserRoleAssignmentRepository(repository);
    }

    @Autowired(required = false)
    void setRefreshTokenHistoryRepository(RefreshTokenHistoryRepository repository) {
        operations.setRefreshTokenHistoryRepository(repository);
    }

    public AuthResponse login(LoginRequest request, AuthClientContext context) {
        return operations.login(request, context);
    }

    public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
        return operations.refresh(request, context);
    }

    public void logout(String userId, String tenantId, String sessionId) {
        operations.logout(userId, tenantId, sessionId);
    }

    public int logoutAll(String userId, String tenantId) {
        return operations.logoutAll(userId, tenantId);
    }

    public List<LoginAttemptResponse> listLoginAttempts(String userId, String email) {
        return operations.loginAttempts(userId, email);
    }

    public IpSecurityStatusResponse ipSecurityStatus(String email, AuthClientContext context) {
        return operations.ipSecurityStatus(email, context);
    }
}
