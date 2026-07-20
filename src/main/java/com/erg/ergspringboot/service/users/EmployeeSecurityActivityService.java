package com.erg.ergspringboot.service.users;

import com.erg.ergspringboot.common.dto.users.EmployeeDtos.SecurityEventPageResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.SecurityEventResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.SecurityFeedItemResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.SecurityFeedPageResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeSecurityActivityService {

    /**
     * A merged feed has to read a window from every source before it can sort globally.
     * Keep that fan-in bounded until the endpoint gets a cursor-based implementation.
     */
    private static final int MAX_MERGED_FETCH_ROWS = 2_000;

    private final SecurityEventRepository securityEventRepository;
    private final UserRepository userRepository;
    private final AuthLoginAttemptRepository loginAttemptRepository;
    private final UserActivityRepository activityRepository;
    private final AuthSessionRepository sessionRepository;

    public EmployeeSecurityActivityService(
            SecurityEventRepository securityEventRepository,
            UserRepository userRepository,
            AuthLoginAttemptRepository loginAttemptRepository,
            UserActivityRepository activityRepository,
            AuthSessionRepository sessionRepository
    ) {
        this.securityEventRepository = securityEventRepository;
        this.userRepository = userRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public SecurityFeedPageResponse listUnifiedFeed(
            String tenantId,
            String userId,
            String sourceType,
            Integer page,
            Integer limit
    ) {
        String tenant = normalizeTenant(tenantId);
        String scopedUserId = trimToNull(userId);
        if (scopedUserId != null && userRepository.findByIdAndTenantId(scopedUserId, tenant).isEmpty()) {
            throw new ResourceNotFoundException("employee not found");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        int required = Math.multiplyExact(safePage, safeLimit);
        rejectDeepPage(required);
        PageRequest fetchWindow = PageRequest.of(0, required);
        String source = trimToNull(sourceType);
        source = source == null ? null : source.toUpperCase(Locale.ROOT);
        if (source != null && !List.of("SECURITY_EVENT", "LOGIN_ATTEMPT", "USER_ACTIVITY", "SESSION").contains(source)) {
            throw new com.erg.ergspringboot.exception.BadRequestException("invalid security feed source");
        }

        List<RawFeedItem> merged = new ArrayList<>();
        long total = 0;
        if (source == null || "SECURITY_EVENT".equals(source)) {
            Page<SecurityEvent> events = scopedUserId == null
                    ? securityEventRepository.findByTenantIdOrderByOccurredAtDescIdDesc(tenant, fetchWindow)
                    : securityEventRepository.findByTenantIdAndUserIdOrderByOccurredAtDescIdDesc(
                            tenant, scopedUserId, fetchWindow);
            total += events.getTotalElements();
            events.forEach(event -> merged.add(fromSecurityEvent(event)));
        }
        if (source == null || "LOGIN_ATTEMPT".equals(source)) {
            Page<AuthLoginAttempt> attempts = scopedUserId == null
                    ? loginAttemptRepository.findByTenantIdOrderByCreatedAtDescIdDesc(tenant, fetchWindow)
                    : loginAttemptRepository.findByTenantIdAndUserIdOrderByCreatedAtDescIdDesc(
                            tenant, scopedUserId, fetchWindow);
            total += attempts.getTotalElements();
            attempts.forEach(attempt -> merged.add(fromLoginAttempt(attempt)));
        }
        if (source == null || "USER_ACTIVITY".equals(source)) {
            Page<UserActivity> activities = scopedUserId == null
                    ? activityRepository.findByTenantIdOrderByCreatedAtDescIdDesc(tenant, fetchWindow)
                    : activityRepository.findByTenantIdAndTargetUserIdOrderByCreatedAtDescIdDesc(
                            tenant, scopedUserId, fetchWindow);
            total += activities.getTotalElements();
            activities.forEach(activity -> merged.add(fromUserActivity(activity)));
        }
        if (source == null || "SESSION".equals(source)) {
            Page<AuthSession> sessions = scopedUserId == null
                    ? sessionRepository.findSecurityFeedByTenant(tenant, fetchWindow)
                    : sessionRepository.findSecurityFeedByTenantAndUser(tenant, scopedUserId, fetchWindow);
            total += sessions.getTotalElements();
            sessions.forEach(session -> merged.add(fromSession(session)));
        }

        merged.sort(Comparator
                .comparing(RawFeedItem::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RawFeedItem::sourceType)
                .thenComparing(RawFeedItem::id));
        int from = Math.min((safePage - 1) * safeLimit, merged.size());
        int to = Math.min(from + safeLimit, merged.size());
        List<RawFeedItem> window = merged.subList(from, to);
        List<String> userIds = window.stream().map(RawFeedItem::userId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, User> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findByTenantIdAndIdInAnyStatus(tenant, userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        List<SecurityFeedItemResponse> items = window.stream()
                .map(item -> toResponse(item, users.get(item.userId())))
                .toList();
        long totalPages = total == 0 ? 0 : (long) Math.ceil((double) total / safeLimit);
        return new SecurityFeedPageResponse(items, safePage, safeLimit, total, totalPages);
    }

    @Transactional(readOnly = true)
    public SecurityEventPageResponse listSecurityEvents(
            String userId,
            String tenantId,
            Integer page,
            Integer limit
    ) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (userRepository.findByIdAndTenantId(userId, normalizedTenant).isEmpty()) {
            throw new ResourceNotFoundException("employee not found");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        rejectDeepPage(Math.multiplyExact(safePage, safeLimit));
        Page<SecurityEvent> events = securityEventRepository
                .findByTenantIdAndUserIdOrderByOccurredAtDescIdDesc(
                        normalizedTenant, userId, PageRequest.of(safePage - 1, safeLimit));
        List<SecurityEventResponse> items = events.getContent().stream().map(this::toResponse).toList();
        return new SecurityEventPageResponse(
                items, safePage, safeLimit, events.getTotalElements(), events.getTotalPages());
    }

    private SecurityEventResponse toResponse(SecurityEvent event) {
        return new SecurityEventResponse(
                event.getId(), event.getEventType(), event.getSeverity(), event.getPortal(), event.getPlatform(),
                event.getResult(), event.getRiskCode(), event.getSessionId(), event.getDeviceId(),
                event.getIpAddress(), event.getReason(), event.getMetadata() == null ? Map.of() : event.getMetadata(),
                event.getOccurredAt() == null ? null : event.getOccurredAt().toString()
        );
    }

    private RawFeedItem fromSecurityEvent(SecurityEvent event) {
        return new RawFeedItem(
                event.getId(), "SECURITY_EVENT", event.getEventType(), event.getTenantId(), event.getUserId(),
                event.getSeverity(), event.getPortal(), event.getPlatform(), event.getResult(), event.getRiskCode(),
                event.getSessionId(), event.getDeviceId(), event.getIpAddress(), event.getReason(),
                event.getMetadata() == null ? Map.of() : event.getMetadata(), event.getOccurredAt());
    }

    private RawFeedItem fromLoginAttempt(AuthLoginAttempt attempt) {
        boolean success = "SUCCESS".equalsIgnoreCase(attempt.getResult());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("attemptedEmail", attempt.getAttemptedEmail() == null ? "" : attempt.getAttemptedEmail());
        metadata.put("deviceName", attempt.getDeviceName() == null ? "" : attempt.getDeviceName());
        return new RawFeedItem(
                attempt.getId(), "LOGIN_ATTEMPT", success ? "LOGIN_SUCCESS" : "LOGIN_FAILURE",
                attempt.getTenantId(), attempt.getUserId(), success ? "INFO" : "WARNING", attempt.getPortal(),
                platform(attempt.getUserAgent()), attempt.getResult(), success ? null : "LOGIN_FAILED",
                null, attempt.getDeviceId(), attempt.getIpAddress(), attempt.getReason(), metadata,
                attempt.getCreatedAt());
    }

    private RawFeedItem fromUserActivity(UserActivity activity) {
        String subject = activity.getTargetUserId() == null ? activity.getUserId() : activity.getTargetUserId();
        return new RawFeedItem(
                activity.getId(), "USER_ACTIVITY", activity.getAction(), activity.getTenantId(), subject,
                "INFO", null, platform(activity.getUserAgent()), "SUCCESS", null, null, null,
                activity.getIpAddress(), activity.getDescription(),
                activity.getMetadata() == null ? Map.of() : activity.getMetadata(), activity.getCreatedAt());
    }

    private RawFeedItem fromSession(AuthSession session) {
        boolean revoked = session.getRevokedAt() != null;
        String eventType = !revoked ? "SESSION_CREATED"
                : "session_replaced".equalsIgnoreCase(session.getRevokedReason())
                        ? "SESSION_REPLACED" : "SESSION_REVOKED";
        return new RawFeedItem(
                session.getId(), "SESSION", eventType, session.getTenantId(), session.getUserId(),
                revoked ? "WARNING" : "INFO", session.getPortal(),
                session.getPlatform() == null ? platform(session.getUserAgent()) : session.getPlatform(),
                revoked ? "REVOKED" : "SUCCESS",
                "SESSION_REPLACED".equals(eventType) ? "SESSION_REPLACED" : null,
                session.getSessionId(), session.getDeviceId(), session.getIpAddress(), session.getRevokedReason(),
                Map.of("deviceName", session.getDeviceName() == null ? "" : session.getDeviceName()),
                revoked ? session.getRevokedAt() : session.getCreatedAt());
    }

    private SecurityFeedItemResponse toResponse(RawFeedItem item, User user) {
        return new SecurityFeedItemResponse(
                item.id(), item.sourceType(), item.eventType(), item.tenantId(), item.userId(),
                user == null ? null : user.getFullName(), user == null ? null : user.getEmail(),
                item.severity(), item.portal(), item.platform(), item.result(), item.riskCode(), item.sessionId(),
                item.deviceId(), item.ipAddress(), item.reason(), item.metadata(),
                item.occurredAt() == null ? null : item.occurredAt().toString());
    }

    private String platform(String userAgent) {
        if (userAgent == null) return null;
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        return normalized.contains("mobile") || normalized.contains("android") || normalized.contains("iphone")
                ? "WEB_MOBILE" : "WEB_DESKTOP";
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "erg" : tenantId.trim();
    }

    private void rejectDeepPage(int requestedRows) {
        if (requestedRows > MAX_MERGED_FETCH_ROWS) {
            throw new com.erg.ergspringboot.exception.BadRequestException(
                    "security feed page exceeds the maximum merged window of " + MAX_MERGED_FETCH_ROWS
                            + " rows; use a cursor or narrower filters");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RawFeedItem(
            String id,
            String sourceType,
            String eventType,
            String tenantId,
            String userId,
            String severity,
            String portal,
            String platform,
            String result,
            String riskCode,
            String sessionId,
            String deviceId,
            String ipAddress,
            String reason,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt
    ) {
    }
}
