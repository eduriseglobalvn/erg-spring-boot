package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.auth.AuthTokenService;
import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional owner of the login flow. Orchestrates rate limiting, {@link CredentialVerifier}
 * and {@link SessionIssuer}; attempts are recorded through {@link LoginAttemptRecorder}.
 * The collaborators are plain objects that run inside this method's transaction.
 */
@Service
public class AuthLoginUseCase {
    private static final String DEFAULT_PORTAL = "lms";

    private final UserRepository users;
    private final LoginAttemptRecorder attemptRecorder;
    private final CredentialVerifier credentialVerifier;
    private final SessionIssuer sessionIssuer;
    private AuthLoginRateLimiter rateLimiter;

    /** Compatibility constructor retained for legacy tests and callers wiring without metrics. */
    public AuthLoginUseCase(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens) {
        this(users, sessions, attempts, passwords, tokens, Optional.empty(),
                new LoginFailureEnforcementService(users, sessions, attempts),
                new AuthLoginRateLimiter(null, true, 30, 10, 20));
    }

    @Autowired
    public AuthLoginUseCase(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens,
            ObjectProvider<MeterRegistry> meterRegistry,
            LoginFailureEnforcementService failureEnforcement,
            AuthLoginRateLimiter rateLimiter) {
        this(users, sessions, attempts, passwords, tokens,
                Optional.ofNullable(meterRegistry.getIfAvailable()), failureEnforcement, rateLimiter);
    }

    private AuthLoginUseCase(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens,
            Optional<MeterRegistry> meterRegistry,
            LoginFailureEnforcementService failureEnforcement,
            AuthLoginRateLimiter rateLimiter) {
        java.util.Objects.requireNonNull(failureEnforcement, "failureEnforcement");
        this.rateLimiter = java.util.Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.users = users;
        this.attemptRecorder = new LoginAttemptRecorder(attempts, meterRegistry, failureEnforcement);
        this.credentialVerifier = new CredentialVerifier(passwords, failureEnforcement, attemptRecorder);
        this.sessionIssuer = new SessionIssuer(users, sessions, tokens, attemptRecorder);
    }

    @Autowired(required = false)
    public void setSecurityEventRepository(SecurityEventRepository securityEvents) {
        sessionIssuer.setSecurityEventRepository(securityEvents);
    }

    @Autowired
    public void setAuthProperties(ErgAuthProperties properties) {
        sessionIssuer.setRapidSessionReplacement(
                properties.getRapidSessionReplacement().getThreshold(),
                properties.getRapidSessionReplacement().getWindowMinutes());
    }

    /** Test/compatibility seam; production receives the collaborator through the constructor. */
    public void setLoginFailureEnforcementService(LoginFailureEnforcementService failureEnforcement) {
        java.util.Objects.requireNonNull(failureEnforcement, "failureEnforcement");
        attemptRecorder.setLoginFailureEnforcementService(failureEnforcement);
        credentialVerifier.setLoginFailureEnforcementService(failureEnforcement);
    }

    /** Test/compatibility seam; production receives the collaborator through the constructor. */
    public void setAuthLoginRateLimiter(AuthLoginRateLimiter rateLimiter) {
        this.rateLimiter = java.util.Objects.requireNonNull(rateLimiter, "rateLimiter");
    }

    @Autowired(required = false)
    public void setUserRoleAssignmentRepository(UserRoleAssignmentRepository roleAssignments) {
        sessionIssuer.setUserRoleAssignmentRepository(roleAssignments);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, AuthClientContext context) {
        String identifier = normalizeIdentifier(request.identifier());
        String portal = normalizePortal(request.portal());
        if (!rateLimiter.allowRequest(context).allowed()) {
            throw new AuthFailure.TooManyAttempts();
        }
        Optional<User> found = users.findForLogin(identifier);
        // One bucket per person: username, email and case variants resolve to the same user id.
        String accountKey = found.map(value -> "user:" + value.getId()).orElse("identifier:" + identifier);
        if (!rateLimiter.allowAccount(accountKey).allowed()) {
            throw new AuthFailure.TooManyAttempts();
        }
        CredentialVerifier.VerifiedLogin verified =
                credentialVerifier.verify(found, identifier, request.password(), context, portal);
        User user = verified.user();

        if (verified.restrictedFirstLogin()) {
            user.setStatus("ACTIVE");
            users.save(user);
        }
        return sessionIssuer.issueTokens(user, context, "erg", portal);
    }

    private String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePortal(String portal) {
        String normalized = portal == null || portal.isBlank() ? DEFAULT_PORTAL : portal.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "admin", "crm", "lms", "elearning", "lcms" -> normalized;
            default -> DEFAULT_PORTAL;
        };
    }
}
