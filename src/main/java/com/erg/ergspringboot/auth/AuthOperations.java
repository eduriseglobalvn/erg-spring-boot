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
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public authentication facade for login, refresh and session lifecycle. */
@Service
public class AuthOperations {
    private final AuthService legacy;
    private final AccountVerificationService verification;

    @Autowired
    public AuthOperations(AuthService legacy, AccountVerificationService verification) {
        this.legacy = legacy;
        this.verification = verification;
    }

    /** Compatibility constructor for focused auth tests. */
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
        return legacy.login(request, context);
    }

    public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
        return legacy.refresh(request, context);
    }

    public void logout(String userId, String tenantId, String sessionId) {
        legacy.logout(userId, tenantId, sessionId);
    }

    public int logoutAll(String userId, String tenantId) {
        return legacy.logoutAll(userId, tenantId);
    }

    public List<LoginAttemptResponse> loginAttempts(String userId, String email) {
        return legacy.listLoginAttempts(userId, email);
    }

    public IpSecurityStatusResponse ipSecurityStatus(String email, AuthClientContext context) {
        return legacy.ipSecurityStatus(email, context);
    }
}
