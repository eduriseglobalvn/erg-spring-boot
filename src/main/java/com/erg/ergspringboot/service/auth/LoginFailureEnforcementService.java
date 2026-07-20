package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits authentication failure controls independently from the login transaction. */
@Service
public class LoginFailureEnforcementService {

    private static final String DEFAULT_TENANT = "erg";
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final long LOGIN_ATTEMPT_THRESHOLD = 5L;
    private static final List<String> FAILED_RESULTS = List.of("FAILED", "FAILURE");

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthLoginAttemptRepository attemptRepository;
    private SecurityEventRepository securityEventRepository;

    public LoginFailureEnforcementService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            AuthLoginAttemptRepository attemptRepository
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
    }

    @Autowired(required = false)
    void setSecurityEventRepository(SecurityEventRepository securityEventRepository) {
        this.securityEventRepository = securityEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInvalidCredentials(
            User user, String attemptedEmail, AuthClientContext context, String portal
    ) {
        recordFailure(user, attemptedEmail, context, portal, "invalid_credentials", true);
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
        OffsetDateTime since = now.minus(LOGIN_ATTEMPT_WINDOW);
        long emailFailures = email == null ? 0 : attemptRepository
                .countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(email, FAILED_RESULTS, since);
        long ipFailures = context == null || context.ipAddress() == null ? 0 : attemptRepository
                .countByIpAddressAndResultInAndCreatedAtAfter(context.ipAddress(), FAILED_RESULTS, since);
        if (emailFailures < LOGIN_ATTEMPT_THRESHOLD && ipFailures < LOGIN_ATTEMPT_THRESHOLD) {
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
            event.setReason("failed_login_threshold_without_account_lock");
            event.setResult("CHALLENGE_REQUIRED");
            event.setRiskCode("CREDENTIAL_ABUSE_THRESHOLD");
            event.setOccurredAt(now);
            securityEventRepository.save(event);
        }
    }
}
