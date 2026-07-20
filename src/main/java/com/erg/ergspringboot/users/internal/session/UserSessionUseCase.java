package com.erg.ergspringboot.users.internal.session;

import com.erg.ergspringboot.common.response.users.UserResponses.RevokeSessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns user session listing and revocation. */
@Service
public class UserSessionUseCase {
    private static final String DEFAULT_TENANT = "erg";
    private final AuthSessionRepository sessions;
    private final UserActivityRepository activities;

    public UserSessionUseCase(AuthSessionRepository sessions, UserActivityRepository activities) {
        this.sessions = sessions;
        this.activities = activities;
    }

    @Transactional(readOnly = true)
    public SessionListResponse list(String userId, String tenantId, String currentSessionId) {
        List<SessionResponse> items = sessions.findByUserIdAndTenantIdOrderByLastActiveAtDesc(userId, tenant(tenantId))
                .stream().map(session -> toResponse(session, currentSessionId)).toList();
        return new SessionListResponse(items);
    }

    @Transactional
    public RevokeSessionResponse revoke(String userId, String tenantId, String sessionId, UserAuditContext audit) {
        AuthSession session = sessions.findBySessionIdAndUserIdAndTenantId(sessionId, userId, tenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        OffsetDateTime revokedAt = OffsetDateTime.now(ZoneOffset.UTC);
        session.setRevokedAt(revokedAt); session.setRevokedReason("user_revoked"); sessions.save(session);
        User target = session.getUser();
        if (target == null) { target = new User(); target.setId(userId); target.setTenantId(tenant(tenantId)); }
        UserAuditContext context = audit == null ? new UserAuditContext(target.getId(), null, null, null) : audit;
        UserActivity activity = new UserActivity(); activity.setId(IdGenerator.newId());
        activity.setUserId(blank(context.actorUserId()) ? target.getId() : context.actorUserId());
        activity.setTenantId(tenant(target.getTenantId())); activity.setAction("EMPLOYEE_SESSION_REVOKED");
        activity.setTargetUserId(target.getId()); activity.setIpAddress(context.ipAddress());
        activity.setUserAgent(context.userAgent()); activity.setRequestId(context.requestId());
        activity.setDescription("Employee session revoked");
        activity.setMetadata(Map.of("before", Map.of("sessionId", sessionId, "revoked", false),
                "after", Map.of("sessionId", sessionId, "revoked", true, "reason", "user_revoked")));
        activity.setCreatedAt(revokedAt); activities.save(activity);
        return new RevokeSessionResponse(true, revokedAt.toString());
    }

    private SessionResponse toResponse(AuthSession session, String currentId) {
        OffsetDateTime lastSeen = session.getLastActiveAt() == null ? session.getCreatedAt() : session.getLastActiveAt();
        return new SessionResponse(session.getSessionId(), session.getDeviceId(), session.getDeviceName(), session.getPortal(),
                session.getIpAddress(), session.getUserAgent(), deviceType(session.getUserAgent()),
                session.getSessionId() != null && session.getSessionId().equals(currentId), session.getRevokedAt() != null,
                session.getRevokedReason(), iso(session.getExpiresAt()), iso(session.getCreatedAt()), iso(lastSeen));
    }
    private String deviceType(String ua) { if (ua == null) return "unknown"; String v = ua.toLowerCase();
        if (v.contains("mobile") || v.contains("android") || v.contains("iphone")) return "mobile";
        if (v.contains("ipad")) return "tablet"; if (v.contains("curl") || v.contains("http")) return "api"; return "desktop"; }
    private String tenant(String value) { return blank(value) ? DEFAULT_TENANT : value.trim(); }
    private String iso(OffsetDateTime value) { return value == null ? null : value.toString(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
