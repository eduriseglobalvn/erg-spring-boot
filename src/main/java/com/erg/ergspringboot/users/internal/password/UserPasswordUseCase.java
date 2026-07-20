package com.erg.ergspringboot.users.internal.password;

import com.erg.ergspringboot.common.request.users.UserRequests.ChangePasswordRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.PasswordChangeResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.auth.PasswordService;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns password rotation and session invalidation. */
@Service
public class UserPasswordUseCase {
    private static final String DEFAULT_TENANT = "erg";
    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final UserActivityRepository activityRepository;
    private final PasswordService passwordService;

    public UserPasswordUseCase(UserRepository users, AuthSessionRepository sessions,
            UserActivityRepository activities, PasswordService passwordService) {
        this.userRepository = users;
        this.sessionRepository = sessions;
        this.activityRepository = activities;
        this.passwordService = passwordService;
    }

    @Transactional
    public PasswordChangeResponse change(String userId, String tenantId, String currentSessionId,
            ChangePasswordRequest request, UserAuditContext audit) {
        User user = userRepository.findByIdAndTenantId(userId, tenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        boolean previousMustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        String previousStatus = user.getStatus();
        if (!passwordService.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BadRequestException("invalid old password");
        }
        user.setPasswordHash(passwordService.hash(request.newPassword()));
        user.setMustChangePassword(false);
        if (Boolean.TRUE.equals(user.getIsProfileCompleted())
                && Boolean.TRUE.equals(user.getRecoveryEmailVerified())) user.setStatus("ACTIVE");
        userRepository.save(user);
        List<AuthSession> sessions = sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(userId, tenant(tenantId));
        List<AuthSession> revoked = sessions.stream()
                .filter(session -> currentSessionId == null || !currentSessionId.equals(session.getSessionId())).toList();
        OffsetDateTime revokedAt = OffsetDateTime.now(ZoneOffset.UTC);
        revoked.forEach(session -> { session.setRevokedAt(revokedAt); session.setRevokedReason("password_changed"); });
        if (!revoked.isEmpty()) sessionRepository.saveAll(revoked);
        record(user, audit, previousMustChange, previousStatus, revoked.size());
        return new PasswordChangeResponse(lifecycle(user), revoked.size());
    }

    private void record(User target, UserAuditContext audit, boolean previousMustChange,
            String previousStatus, int revoked) {
        UserAuditContext context = audit == null ? new UserAuditContext(target.getId(), null, null, null) : audit;
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setUserId(blank(context.actorUserId()) ? target.getId() : context.actorUserId());
        activity.setTenantId(tenant(target.getTenantId())); activity.setAction("ACCOUNT_PASSWORD_CHANGED");
        activity.setTargetUserId(target.getId()); activity.setIpAddress(context.ipAddress());
        activity.setUserAgent(context.userAgent()); activity.setRequestId(context.requestId());
        activity.setDescription("Account password changed");
        activity.setMetadata(Map.of("before", Map.of("mustChangePassword", previousMustChange,
                "status", previousStatus == null ? "" : previousStatus),
                "after", Map.of("mustChangePassword", false, "status", target.getStatus(), "revokedSessions", revoked)));
        activity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC)); activityRepository.save(activity);
    }

    private AccountLifecycleResponse lifecycle(User user) {
        List<String> next = new ArrayList<>();
        if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) next.add("COMPLETE_PROFILE");
        if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) next.add("VERIFY_RECOVERY_EMAIL");
        if (Boolean.TRUE.equals(user.getMustChangePassword())) next.add("CHANGE_PASSWORD");
        String status = next.isEmpty() ? "ACTIVE" : ("INVITED".equalsIgnoreCase(user.getStatus()) ? "PROVISIONED" : "FIRST_LOGIN_RESTRICTED");
        String email = user.getRecoveryEmail();
        String masked = email == null || !email.contains("@") ? null : email.substring(0, 1) + "***" + email.substring(email.indexOf('@'));
        return new AccountLifecycleResponse(status, Boolean.TRUE.equals(user.getIsProfileCompleted()), masked,
                Boolean.TRUE.equals(user.getRecoveryEmailVerified()), Boolean.TRUE.equals(user.getMustChangePassword()), List.copyOf(next));
    }
    private String tenant(String value) { return blank(value) ? DEFAULT_TENANT : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
