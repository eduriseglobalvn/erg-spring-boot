package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.common.request.users.UserRequests.RecoveryEmailChallengeRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.RecoveryEmailChallengeResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RecoveryEmailVerificationResponse;
import com.erg.ergspringboot.auth.internal.login.AuthLoginUseCase;
import com.erg.ergspringboot.auth.internal.refresh.AuthRefreshUseCase;
import com.erg.ergspringboot.auth.internal.security.AuthSecurityQueryUseCase;
import com.erg.ergspringboot.auth.internal.session.AuthSessionLifecycleUseCase;
import java.util.List;
import org.springframework.stereotype.Service;

/** Public authentication facade for login, refresh and session lifecycle. */
@Service
public class AuthOperations {
    private final AuthLoginUseCase loginFlow;
    private final AuthRefreshUseCase refreshFlow;
    private final AuthSessionLifecycleUseCase sessionLifecycle;
    private final AuthSecurityQueryUseCase securityQueries;
    private final AccountVerificationService verification;

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
        this.verification = verification;
    }

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
        return loginFlow.login(request, context);
    }

    public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
        return refreshFlow.refresh(request, context);
    }

    public void logout(String userId, String tenantId, String sessionId) {
        sessionLifecycle.logout(userId, tenantId, sessionId);
    }

    public int logoutAll(String userId, String tenantId) {
        return sessionLifecycle.logoutAll(userId, tenantId);
    }

    public List<LoginAttemptResponse> loginAttempts(String userId, String email) {
        return securityQueries.loginAttempts(userId, email);
    }

    public IpSecurityStatusResponse ipSecurityStatus(String email, AuthClientContext context) {
        return securityQueries.ipStatus(email, context);
    }
}
