package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.users.persistence.auth.AuthLoginAttempt;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records login attempts: successful attempts are persisted, logged and counted here; failures
 * are handed to {@link LoginFailureEnforcementService}, which commits them in its own transaction.
 * Runs inside the caller's transaction; it is not a Spring bean and adds no proxy boundary.
 */
final class LoginAttemptRecorder {
    // Logged under the use-case category so existing log routing and alerts keep matching.
    private static final Logger log = LoggerFactory.getLogger(AuthLoginUseCase.class);

    private static final String DEFAULT_TENANT = "erg";

    private final AuthLoginAttemptRepository attempts;
    private final Optional<MeterRegistry> meterRegistry;
    private LoginFailureEnforcementService failureEnforcement;

    LoginAttemptRecorder(
            AuthLoginAttemptRepository attempts,
            Optional<MeterRegistry> meterRegistry,
            LoginFailureEnforcementService failureEnforcement) {
        this.attempts = attempts;
        this.meterRegistry = meterRegistry;
        this.failureEnforcement = Objects.requireNonNull(failureEnforcement, "failureEnforcement");
    }

    void setLoginFailureEnforcementService(LoginFailureEnforcementService failureEnforcement) {
        this.failureEnforcement = Objects.requireNonNull(failureEnforcement, "failureEnforcement");
    }

    void recordLoginAttempt(
            String userId, String email, AuthClientContext context,
            String portal, String result, String reason) {
        AuthLoginAttempt attempt = new AuthLoginAttempt();
        attempt.setId(IdGenerator.newId());
        attempt.setTenantId(DEFAULT_TENANT);
        attempt.setUserId(userId);
        attempt.setAttemptedEmail(email == null ? null : normalizeEmail(email));
        attempt.setIpAddress(context.ipAddress());
        attempt.setUserAgent(context.userAgent());
        attempt.setDeviceId(firstNonBlank(context.deviceId(), "web"));
        attempt.setDeviceName(firstNonBlank(context.deviceName(), "Browser"));
        attempt.setPortal(portal);
        attempt.setResult(result);
        attempt.setReason(reason);
        attempt.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        attempts.save(attempt);
        if ("SUCCESS".equalsIgnoreCase(result)) {
            log.info("AUTH_LOGIN_SUCCESS user={} email={} ip={} portal={} device={} tenant={}",
                    userId, email, context.ipAddress(), portal, context.deviceName(), DEFAULT_TENANT);
        } else {
            log.warn("AUTH_LOGIN_FAILED user={} email={} ip={} portal={} reason={}",
                    userId, email, context.ipAddress(), portal, reason);
        }
        meterRegistry.ifPresent(registry -> registry
                .counter("erg.auth.login.count", "result", result == null ? "none" : result)
                .increment());
    }

    void recordFailure(
            User user, String email, AuthClientContext context,
            String portal, String reason, boolean enforceThreshold) {
        // Failure records commit in their own transaction so the thrown AuthFailure cannot roll them back.
        if (enforceThreshold && "invalid_credentials".equals(reason)) {
            failureEnforcement.recordInvalidCredentials(user, email, context, portal);
        } else {
            failureEnforcement.recordFailure(user, email, context, portal, reason, enforceThreshold);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
