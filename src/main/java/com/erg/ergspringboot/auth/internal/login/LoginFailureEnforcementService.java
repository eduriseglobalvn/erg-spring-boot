package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits authentication failure controls independently from the login transaction. */
@Service
public class LoginFailureEnforcementService {

    private static final String DEFAULT_TENANT = "erg";
    private static final List<String> FAILED_RESULTS = List.of("FAILED", "FAILURE");
    static final String INVALID_CREDENTIALS = "invalid_credentials";
    static final String TEMPORARILY_LOCKED = "account_temporarily_locked";
    /** Upper bound on history scanned per lockout check; exceeds a full block of rate-limited attempts. */
    private static final int LOCKOUT_SCAN_LIMIT = 200;

    private final int maxFailedLogin;
    private final Duration failedLoginWindow;
    private final Duration blockDuration;

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthLoginAttemptRepository attemptRepository;
    private SecurityEventRepository securityEventRepository;

    @Autowired
    public LoginFailureEnforcementService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            AuthLoginAttemptRepository attemptRepository,
            ErgAuthProperties properties
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
        this.maxFailedLogin = Math.max(1, properties.getMaxFailedLogin());
        this.failedLoginWindow = properties.getFailedLoginWindow();
        this.blockDuration = properties.getBlockDuration();
    }

    /** Compatibility constructor using the documented defaults (5 failures / 15m window / 15m block). */
    public LoginFailureEnforcementService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            AuthLoginAttemptRepository attemptRepository
    ) {
        this(userRepository, sessionRepository, attemptRepository, new ErgAuthProperties());
    }

    /**
     * Account lockout derived from durable attempt history: {@code maxFailedLogin} consecutive
     * wrong passwords (no success in between) within {@code failedLoginWindow} lock the account
     * until {@code blockDuration} after the latest one. Attempts refused while locked are not
     * counted, so a lock is not extended indefinitely by the attacker.
     */
    @Transactional(readOnly = true)
    public boolean isTemporarilyLocked(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<OffsetDateTime> failures = new java.util.ArrayList<>();
        for (AuthLoginAttempt attempt : attemptRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, LOCKOUT_SCAN_LIMIT))) {
            if ("SUCCESS".equalsIgnoreCase(attempt.getResult())) {
                break;
            }
            if (attempt.getCreatedAt() != null && INVALID_CREDENTIALS.equals(attempt.getReason())) {
                failures.add(attempt.getCreatedAt());
                if (failures.size() >= maxFailedLogin) {
                    break;
                }
            }
        }
        if (failures.size() < maxFailedLogin) {
            return false;
        }
        OffsetDateTime latest = failures.getFirst();
        OffsetDateTime oldest = failures.getLast();
        return !oldest.isBefore(latest.minus(failedLoginWindow)) && latest.plus(blockDuration).isAfter(now);
    }

    @Autowired(required = false)
    void setSecurityEventRepository(SecurityEventRepository securityEventRepository) {
        this.securityEventRepository = securityEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInvalidCredentials(
            User user, String attemptedEmail, AuthClientContext context, String portal
    ) {
        recordFailure(user, attemptedEmail, context, portal, INVALID_CREDENTIALS, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            User user,
            String attemptedEmail,
            AuthClientContext context,
            String portal,
            String reason,
            boolean enforceThreshold
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String email = attemptedEmail == null ? null : attemptedEmail.trim().toLowerCase();
        String tenantId = user == null || user.getTenantId() == null || user.getTenantId().isBlank()
                ? DEFAULT_TENANT
                : user.getTenantId().trim();

        AuthLoginAttempt attempt = new AuthLoginAttempt();
        attempt.setId(IdGenerator.newId());
        attempt.setTenantId(tenantId);
        attempt.setUserId(user == null ? null : user.getId());
        attempt.setAttemptedEmail(email);
        attempt.setIpAddress(context == null ? null : context.ipAddress());
        attempt.setUserAgent(context == null ? null : context.userAgent());
        attempt.setDeviceId(context == null || context.deviceId() == null ? "web" : context.deviceId());
        attempt.setDeviceName(context == null || context.deviceName() == null ? "Browser" : context.deviceName());
        attempt.setPortal(portal);
        attempt.setResult("FAILED");
        attempt.setReason(reason);
        attempt.setCreatedAt(now);
        attemptRepository.save(attempt);

        if (!enforceThreshold || user == null) {
            return;
        }
        OffsetDateTime since = now.minus(failedLoginWindow);
        long emailFailures = email == null ? 0 : attemptRepository
                .countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(email, FAILED_RESULTS, since);
        long ipFailures = context == null || context.ipAddress() == null ? 0 : attemptRepository
                .countByIpAddressAndResultInAndCreatedAtAfter(context.ipAddress(), FAILED_RESULTS, since);
        if (emailFailures < maxFailedLogin && ipFailures < maxFailedLogin) {
            return;
        }

        if (securityEventRepository != null) {
            SecurityEvent event = new SecurityEvent();
            event.setId(IdGenerator.newId());
            event.setTenantId(tenantId);
            event.setUserId(user.getId());
            event.setEventType("LOGIN_THROTTLE_REQUIRED");
            event.setSeverity("HIGH");
            event.setPortal(portal);
            event.setDeviceId(context == null ? null : context.deviceId());
            event.setIpAddress(context == null ? null : context.ipAddress());
            event.setReason("failed_login_threshold_reached");
            event.setResult("CHALLENGE_REQUIRED");
            event.setRiskCode("CREDENTIAL_ABUSE_THRESHOLD");
            event.setOccurredAt(now);
            securityEventRepository.save(event);
        }
    }
}
