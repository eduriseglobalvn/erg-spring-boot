package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.service.auth.AuthService;
import com.erg.ergspringboot.service.auth.AccountVerificationService;
import com.erg.ergspringboot.common.request.users.UserRequests.RecoveryEmailChallengeRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.RecoveryEmailChallengeResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RecoveryEmailVerificationResponse;
import com.erg.ergspringboot.auth.internal.login.AuthLoginUseCase;
import com.erg.ergspringboot.auth.internal.refresh.AuthRefreshUseCase;
import com.erg.ergspringboot.auth.internal.security.AuthSecurityQueryUseCase;
import com.erg.ergspringboot.auth.internal.session.AuthSessionLifecycleUseCase;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public authentication facade for login, refresh and session lifecycle. */
@Service
public class AuthOperations {
    private final AuthLoginUseCase loginFlow;
    private final AuthRefreshUseCase refreshFlow;
    private final AuthSessionLifecycleUseCase sessionLifecycle;
    private final AuthSecurityQueryUseCase securityQueries;
    private final AuthService legacy;
    private final AccountVerificationService verification;

    @Autowired
    public AuthOperations(
            AuthLoginUseCase loginFlow,
            AuthRefreshUseCase refreshFlow,
            AuthSessionLifecycleUseCase sessionLifecycle,
            AuthSecurityQueryUseCase securityQueries,
            AccountVerificationService verification) {
        this.loginFlow = loginFlow;
        this.refreshFlow = refreshFlow;
        this.sessionLifecycle = sessionLifecycle;
        this.securityQueries = securityQueries;
        this.legacy = null;
        this.verification = verification;
    }

    /** Compatibility constructor for adapters/tests while the legacy service is retired. */
    @Deprecated
    public AuthOperations(AuthService legacy, AccountVerificationService verification) {
        this.loginFlow = null;
        this.refreshFlow = null;
        this.sessionLifecycle = null;
        this.securityQueries = null;
        this.legacy = legacy;
        this.verification = verification;
    }

    /** Compatibility constructor for focused auth tests. */
    @Deprecated
    public AuthOperations(AuthService legacy) { this(legacy, null); }

    public RecoveryEmailChallengeResponse requestRecoveryEmailChallenge(
            String userId, String tenantId, RecoveryEmailChallengeRequest request, AuthAuditContext audit) {
        return requireVerification().requestRecoveryEmailChallenge(userId, tenantId, request.recoveryEmail(), audit.toLegacy());
    }

    public RecoveryEmailVerificationResponse verifyRecoveryEmail(
            String userId, String tenantId, String challengeId, String otp, AuthAuditContext audit) {
        return requireVerification().verifyRecoveryEmail(userId, tenantId, challengeId, otp, audit.toLegacy());
    }

    private AccountVerificationService requireVerification() {
        if (verification == null) throw new IllegalStateException("account verification service is not configured");
        return verification;
    }

    public AuthResponse login(LoginRequest request, AuthClientContext context) {
        return legacy != null ? legacy.login(request, context) : loginFlow.login(request, context);
    }

    public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
        return legacy != null ? legacy.refresh(request, context) : refreshFlow.refresh(request, context);
    }

    public void logout(String userId, String tenantId, String sessionId) {
        if (legacy != null) legacy.logout(userId, tenantId, sessionId);
        else sessionLifecycle.logout(userId, tenantId, sessionId);
    }

    public int logoutAll(String userId, String tenantId) {
        return legacy != null
                ? legacy.logoutAll(userId, tenantId)
                : sessionLifecycle.logoutAll(userId, tenantId);
    }

    public List<LoginAttemptResponse> loginAttempts(String userId, String email) {
        return legacy != null
                ? legacy.listLoginAttempts(userId, email)
                : securityQueries.loginAttempts(userId, email);
    }

    public IpSecurityStatusResponse ipSecurityStatus(String email, AuthClientContext context) {
        return legacy != null
                ? legacy.ipSecurityStatus(email, context)
                : securityQueries.ipStatus(email, context);
    }

    public void setSecurityEventRepository(
            com.erg.ergspringboot.repository.auth.SecurityEventRepository repository) {
        if (loginFlow != null) loginFlow.setSecurityEventRepository(repository);
        if (refreshFlow != null) refreshFlow.setSecurityEventRepository(repository);
    }

    public void setLoginFailureEnforcementService(
            com.erg.ergspringboot.service.auth.LoginFailureEnforcementService service) {
        if (loginFlow != null) loginFlow.setLoginFailureEnforcementService(service);
    }

    public void setAuthLoginRateLimiter(
            com.erg.ergspringboot.service.auth.AuthLoginRateLimiter limiter) {
        if (loginFlow != null) loginFlow.setAuthLoginRateLimiter(limiter);
    }

    public void setUserRoleAssignmentRepository(
            com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository repository) {
        if (loginFlow != null) loginFlow.setUserRoleAssignmentRepository(repository);
    }

    public void setRefreshTokenHistoryRepository(
            com.erg.ergspringboot.repository.auth.RefreshTokenHistoryRepository repository) {
        if (refreshFlow != null) refreshFlow.setRefreshTokenHistoryRepository(repository);
    }
}
