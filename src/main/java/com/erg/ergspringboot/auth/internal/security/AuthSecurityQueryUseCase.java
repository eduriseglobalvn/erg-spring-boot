package com.erg.ergspringboot.auth.internal.security;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns bounded login-attempt history and IP/email abuse status queries. */
@Service
@Transactional(readOnly = true)
public class AuthSecurityQueryUseCase {

    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final int LOGIN_ATTEMPT_THRESHOLD = 5;
    private static final List<String> FAILED_LOGIN_RESULTS = List.of("FAILED", "FAILURE");

    private final AuthLoginAttemptRepository attempts;

    public AuthSecurityQueryUseCase(AuthLoginAttemptRepository attempts) {
        this.attempts = attempts;
    }

    public List<LoginAttemptResponse> loginAttempts(String userId, String email) {
        PageRequest limit = PageRequest.of(0, 100);
        List<AuthLoginAttempt> results;
        if (!isBlank(userId)) {
            results = attempts.findByUserIdOrderByCreatedAtDesc(userId, limit);
        } else if (!isBlank(email)) {
            results = attempts.findByAttemptedEmailIgnoreCaseOrderByCreatedAtDesc(
                    normalizeEmail(email), limit);
        } else {
            results = List.of();
        }
        return results.stream().map(this::response).toList();
    }

    public IpSecurityStatusResponse ipStatus(String email, AuthClientContext context) {
        String ip = context.ipAddress();
        long failedAttemptsIp = isBlank(ip)
                ? 0
                : attempts.countByIpAddressAndResultIn(ip, FAILED_LOGIN_RESULTS);
        Long failedAttemptsEmail = null;
        if (!isBlank(email)) {
            failedAttemptsEmail = attempts.countByAttemptedEmailIgnoreCaseAndResultIn(
                    normalizeEmail(email), FAILED_LOGIN_RESULTS);
        }
        OffsetDateTime since = now().minus(LOGIN_ATTEMPT_WINDOW);
        long recentFailedAttemptsIp = isBlank(ip)
                ? 0
                : attempts.countByIpAddressAndResultInAndCreatedAtAfter(
                        ip, FAILED_LOGIN_RESULTS, since);
        Long recentFailedAttemptsEmail = null;
        if (!isBlank(email)) {
            recentFailedAttemptsEmail = attempts
                    .countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(
                            normalizeEmail(email), FAILED_LOGIN_RESULTS, since);
        }
        boolean blocked = recentFailedAttemptsIp >= LOGIN_ATTEMPT_THRESHOLD
                || (recentFailedAttemptsEmail != null
                && recentFailedAttemptsEmail >= LOGIN_ATTEMPT_THRESHOLD);

        return new IpSecurityStatusResponse(
                ip,
                blocked,
                false,
                null,
                null,
                false,
                failedAttemptsIp,
                failedAttemptsEmail,
                LOGIN_ATTEMPT_WINDOW.toSeconds(),
                LOGIN_ATTEMPT_THRESHOLD);
    }

    private LoginAttemptResponse response(AuthLoginAttempt attempt) {
        return new LoginAttemptResponse(
                attempt.getId(),
                attempt.getTenantId(),
                attempt.getUserId(),
                attempt.getAttemptedEmail(),
                attempt.getIpAddress(),
                attempt.getCountryCode(),
                attempt.getCountryName(),
                attempt.getContinentCode(),
                attempt.getUserAgent(),
                attempt.getDeviceId(),
                attempt.getDeviceName(),
                attempt.getPortal(),
                attempt.getResult(),
                attempt.getReason(),
                toIso(attempt.getCreatedAt()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String toIso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
