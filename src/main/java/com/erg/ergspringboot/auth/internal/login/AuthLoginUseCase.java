package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import com.erg.ergspringboot.users.persistence.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
import com.erg.ergspringboot.users.persistence.auth.SecurityEventRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.auth.AuthTokenService;
import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional owner of the login flow. Orchestrates rate limiting, {@link CredentialVerifier}
 * and {@link SessionIssuer}; attempts are recorded through {@link LoginAttemptRecorder}.
 * The password is hashed first ({@link CheckedPassword}), then the collaborators run inside one
 * transaction: hashing inside it parked every pooled connection during a login burst.
 */
@Service
public class AuthLoginUseCase {
    private static final String DEFAULT_PORTAL = "lms";

    private final UserRepository users;
    private final LoginAttemptRecorder attemptRecorder;
    private final CredentialVerifier credentialVerifier;
    private final SessionIssuer sessionIssuer;
    private final PasswordService passwords;
    private TransactionOperations transaction = TransactionOperations.withoutTransaction();
    private AuthLoginRateLimiter rateLimiter;
    private LoginPasswordCache passwordCache;

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
        this.passwords = passwords;
        this.credentialVerifier = new CredentialVerifier(failureEnforcement, attemptRecorder);
        this.sessionIssuer = new SessionIssuer(users, sessions, tokens, attemptRecorder);
    }

    @Autowired(required = false)
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
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
    void setLoginPasswordCache(LoginPasswordCache passwordCache) {
        this.passwordCache = passwordCache;
    }

    @Autowired(required = false)
    public void setUserRoleAssignmentRepository(UserRoleAssignmentRepository roleAssignments) {
        sessionIssuer.setUserRoleAssignmentRepository(roleAssignments);
    }

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
        // Argon2 runs before the transaction: a login may queue up to 10 s for a hash thread, and must not hold a
        // pooled connection meanwhile (a 2000-login burst parked up to 29 connections idle in transaction).
        CheckedPassword password = CheckedPassword.check(passwords, passwordCache, found, request.password());
        return transaction.execute(status -> loginVerified(identifier, portal, password, context,
                Boolean.TRUE.equals(request.rememberMe())));
    }

    private AuthResponse loginVerified(
            String identifier, String portal, CheckedPassword password, AuthClientContext context, boolean rememberMe) {
        // Re-read inside the transaction so the account updates below act on a managed, current row.
        Optional<User> found = users.findForLogin(identifier);
        CredentialVerifier.VerifiedLogin verified;
        try {
            verified = credentialVerifier.verify(found, identifier, password, context, portal);
        } catch (AuthFailure.InvalidCredentials wrongPassword) {
            rateLimiter.recordFailedLogin(context);
            throw wrongPassword;
        }
        User user = verified.user();

        if (verified.restrictedFirstLogin()) {
            user.setStatus("ACTIVE");
            users.save(user);
        }
        return sessionIssuer.issueTokens(
                user, context, "erg", portal, rememberMe);
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
