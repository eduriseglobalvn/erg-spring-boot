package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.common.response.users.UserResponses.RecoveryEmailChallengeResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RecoveryEmailVerificationResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.exception.AccountLifecycleException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.AccountVerificationCode;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.AccountVerificationCodeRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountVerificationService {

    private static final String PURPOSE = "RECOVERY_EMAIL";
    private static final Duration EXPIRY = Duration.ofMinutes(10);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final AccountVerificationCodeRepository codeRepository;
    private final AccountMailSender mailSender;
    private final PasswordService passwordService;
    private final UserActivityRepository userActivityRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountVerificationService(
            UserRepository userRepository,
            AccountVerificationCodeRepository codeRepository,
            AccountMailSender mailSender,
            PasswordService passwordService,
            UserActivityRepository userActivityRepository
    ) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.mailSender = mailSender;
        this.passwordService = passwordService;
        this.userActivityRepository = userActivityRepository;
    }

    @Transactional
    public RecoveryEmailChallengeResponse requestRecoveryEmailChallenge(
            String userId, String tenantId, String requestedEmail
    ) {
        return requestRecoveryEmailChallenge(userId, tenantId, requestedEmail, null);
    }

    @Transactional
    public RecoveryEmailChallengeResponse requestRecoveryEmailChallenge(
            String userId, String tenantId, String requestedEmail, AuditContext auditContext
    ) {
        User user = findUser(userId, tenantId);
        String email = normalizeEmail(requestedEmail);
        if (email.equalsIgnoreCase(user.getEmail())) {
            throw failure(HttpStatus.BAD_REQUEST, "RECOVERY_EMAIL_REQUIRED",
                    "recovery email must differ from primary email");
        }
        if (userRepository.existsByRecoveryEmailIgnoreCaseAndIdNot(email, userId)) {
            throw failure(HttpStatus.CONFLICT, "RECOVERY_EMAIL_CONFLICT",
                    "recovery email is already used by another account");
        }
        OffsetDateTime now = OffsetDateTime.now();
        codeRepository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(userId, PURPOSE)
                .ifPresent(existing -> {
                    if (existing.getLastSentAt() != null && existing.getLastSentAt().plus(COOLDOWN).isAfter(now)) {
                        throw failure(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                                "please wait before requesting another code");
                    }
                    existing.setConsumedAt(now);
                    codeRepository.saveAndFlush(existing);
                });

        String rawCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        AccountVerificationCode challenge = new AccountVerificationCode();
        challenge.setId(IdGenerator.newId());
        challenge.setUserId(userId);
        challenge.setTenantId(normalizeTenant(tenantId));
        challenge.setPurpose(PURPOSE);
        challenge.setCodeHash(passwordService.hash(rawCode));
        challenge.setTargetEmail(email);
        challenge.setAttempts(0);
        challenge.setMaxAttempts(5);
        challenge.setExpiresAt(now.plus(EXPIRY));
        challenge.setLastSentAt(now);
        codeRepository.save(challenge);
        mailSender.sendRecoveryEmailCode(email, user.getFullName(), rawCode);
        recordActivity(user, auditContext, "RECOVERY_EMAIL_CHALLENGE_SENT",
                Map.of("destination", maskEmail(email), "expiresAt", challenge.getExpiresAt().toString()));
        return new RecoveryEmailChallengeResponse(
                challenge.getId(), maskEmail(email), challenge.getExpiresAt().toString(), COOLDOWN.toSeconds());
    }

    @Transactional
    public RecoveryEmailVerificationResponse verifyRecoveryEmail(
            String userId, String tenantId, String challengeId, String otp
    ) {
        return verifyRecoveryEmail(userId, tenantId, challengeId, otp, null);
    }

    @Transactional
    public RecoveryEmailVerificationResponse verifyRecoveryEmail(
            String userId, String tenantId, String challengeId, String otp, AuditContext auditContext
    ) {
        User user = findUser(userId, tenantId);
        AccountVerificationCode challenge = codeRepository.findById(challengeId)
                .filter(code -> userId.equals(code.getUserId()))
                .filter(code -> normalizeTenant(tenantId).equals(normalizeTenant(code.getTenantId())))
                .filter(code -> PURPOSE.equals(code.getPurpose()))
                .orElseThrow(() -> failure(HttpStatus.BAD_REQUEST, "OTP_INVALID", "invalid verification code"));
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.getConsumedAt() != null) {
            throw failure(HttpStatus.BAD_REQUEST, "OTP_INVALID", "verification code was already used");
        }
        if (challenge.getExpiresAt() == null || !challenge.getExpiresAt().isAfter(now)) {
            throw failure(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "verification code has expired");
        }
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw failure(HttpStatus.BAD_REQUEST, "OTP_INVALID", "verification attempts exceeded");
        }
        if (!passwordService.matches(otp.trim(), challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            codeRepository.save(challenge);
            throw failure(HttpStatus.BAD_REQUEST, "OTP_INVALID", "invalid verification code");
        }
        challenge.setConsumedAt(now);
        user.setRecoveryEmail(challenge.getTargetEmail());
        user.setRecoveryEmailVerified(true);
        codeRepository.save(challenge);
        userRepository.save(user);
        recordActivity(user, auditContext, "RECOVERY_EMAIL_VERIFIED",
                Map.of("destination", maskEmail(challenge.getTargetEmail()), "challengeId", challenge.getId()));
        return new RecoveryEmailVerificationResponse(true, lifecycle(user));
    }

    private void recordActivity(User user, AuditContext auditContext, String action, Map<String, Object> after) {
        AuditContext context = auditContext == null
                ? new AuditContext(user.getId(), null, null, null)
                : auditContext;
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setUserId(context.actorUserId() == null || context.actorUserId().isBlank()
                ? user.getId() : context.actorUserId());
        activity.setTenantId(normalizeTenant(user.getTenantId()));
        activity.setAction(action);
        activity.setTargetUserId(user.getId());
        activity.setIpAddress(context.ipAddress());
        activity.setUserAgent(context.userAgent());
        activity.setDescription(action.replace('_', ' ').toLowerCase());
        activity.setRequestId(context.requestId());
        activity.setMetadata(Map.of("before", Map.of(), "after", after));
        activity.setCreatedAt(OffsetDateTime.now());
        userActivityRepository.save(activity);
    }

    private AccountLifecycleResponse lifecycle(User user) {
        java.util.List<String> nextSteps = new java.util.ArrayList<>();
        if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) nextSteps.add("COMPLETE_PROFILE");
        if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) nextSteps.add("VERIFY_RECOVERY_EMAIL");
        if (Boolean.TRUE.equals(user.getMustChangePassword())) nextSteps.add("CHANGE_PASSWORD");
        return new AccountLifecycleResponse(
                nextSteps.isEmpty() ? "ACTIVE" : "FIRST_LOGIN_RESTRICTED",
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                maskEmail(user.getRecoveryEmail()),
                Boolean.TRUE.equals(user.getRecoveryEmailVerified()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                java.util.List.copyOf(nextSteps)
        );
    }

    private User findUser(String userId, String tenantId) {
        return userRepository.findByIdAndTenantId(userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw failure(HttpStatus.BAD_REQUEST, "RECOVERY_EMAIL_REQUIRED", "valid recovery email is required");
        }
        return email.trim().toLowerCase();
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "erg" : tenantId.trim();
    }

    private String maskEmail(String email) {
        int separator = email.indexOf('@');
        return email.substring(0, 1) + "***" + email.substring(separator);
    }

    private AccountLifecycleException failure(HttpStatus status, String code, String message) {
        return new AccountLifecycleException(status, code, message);
    }
}
