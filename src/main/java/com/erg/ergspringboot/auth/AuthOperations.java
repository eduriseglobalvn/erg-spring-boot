package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.service.auth.AuthService;
import java.util.List;
import org.springframework.stereotype.Service;

/** Public authentication facade for login, refresh and session lifecycle. */
@Service
public class AuthOperations {
    private final AuthService legacy;

    public AuthOperations(AuthService legacy) {
        this.legacy = legacy;
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
