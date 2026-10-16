package com.erg.ergspringboot.service.authorization;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationMutationAuditService {
    private static final int BATCH_SIZE = 500;
    private final UserRepository userRepository;
    private final UserActivityRepository activityRepository;
    private final SecurityEventRepository securityEventRepository;
    private final AuditOperations auditOperations;

    @Autowired
    public AuthorizationMutationAuditService(
            UserRepository userRepository,
            UserActivityRepository activityRepository,
            SecurityEventRepository securityEventRepository,
            AuditOperations auditOperations
    ) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.securityEventRepository = securityEventRepository;
        this.auditOperations = auditOperations;
    }

    /** Compatibility constructor retained for legacy unit tests and callers. */
    public AuthorizationMutationAuditService(
            UserRepository userRepository,
            UserActivityRepository activityRepository,
            SecurityEventRepository securityEventRepository
    ) {
        this(userRepository, activityRepository, securityEventRepository, null);
    }

    @Transactional
    public void authorizationChanged(
            Collection<UserRef> users,
            String roleId,
            String reason,
            AuditContext context
    ) {
        if (users == null || users.isEmpty()) return;
        Map<String, LinkedHashSet<String>> idsByTenant = new LinkedHashMap<>();
        for (UserRef ref : users) {
            if (ref == null || ref.userId() == null || ref.userId().isBlank()) continue;
            idsByTenant.computeIfAbsent(normalizeTenant(ref.tenantId()), ignored -> new LinkedHashSet<>())
                    .add(ref.userId().trim());
        }
        for (Map.Entry<String, LinkedHashSet<String>> entry : idsByTenant.entrySet()) {
            List<String> ids = List.copyOf(entry.getValue());
            for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
                List<String> chunk = ids.subList(from, Math.min(ids.size(), from + BATCH_SIZE));
                applyBatch(entry.getKey(), chunk, roleId, reason, context);
            }
        }
    }

    private void applyBatch(
            String tenantId, List<String> userIds, String roleId, String reason, AuditContext context) {
        List<User> targets = userRepository.lockAuthzVersions(tenantId, userIds);
        Map<String, User> byId = targets.stream().collect(java.util.stream.Collectors.toMap(User::getId, user -> user));
        if (userIds.stream().anyMatch(id -> !byId.containsKey(id))) {
            throw new ResourceNotFoundException("authorization subject not found");
        }
        List<UserActivity> activities = new ArrayList<>(targets.size());
        List<SecurityEvent> events = new ArrayList<>(targets.size());
        for (String userId : userIds) {
            User target = byId.get(userId);
            long oldVersion = target.getAuthzVersion() == null ? 1L : target.getAuthzVersion();
            long newVersion = Math.addExact(oldVersion, 1L);
            target.setAuthzVersion(newVersion);
            activities.add(targetActivity(target, roleId, reason, oldVersion, newVersion, context));
            events.add(versionEvent(target, roleId, reason, oldVersion, newVersion, context));
        }
        userRepository.saveAll(targets);
        activityRepository.saveAll(activities);
        securityEventRepository.saveAll(events);
        if (auditOperations != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("roleId", roleId);
            details.put("reason", reason);
            details.put("affectedUserIds", userIds);
            details.put("affectedCount", userIds.size());
            auditOperations.record(new AuditRecord(
                    tenantId,
                    requiredActor(context),
                    "TENANT",
                    tenantId,
                    "AUTHORIZATION_CHANGED",
                    "USER_AUTHORIZATION",
                    roleId,
                    AuditOutcome.SUCCESS,
                    context == null ? null : context.requestId(),
                    context == null ? null : context.requestId(),
                    null,
                    details,
                    idempotencyKey("authorization", tenantId, roleId, reason, context, userIds),
                    OffsetDateTime.now(),
                    null));
        }
    }

    @Transactional
    public void recordRoleMutation(
            String tenantId,
            String roleId,
            String action,
            AuditContext context,
            Map<String, Object> details
    ) {
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setTenantId(normalizeTenant(tenantId));
        activity.setUserId(requiredActor(context));
        activity.setAction(action);
        activity.setDescription("Role authorization definition changed");
        activity.setRequestId(context == null ? null : context.requestId());
        activity.setIpAddress(context == null ? null : context.ipAddress());
        activity.setUserAgent(context == null ? null : context.userAgent());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("roleId", roleId);
        if (details != null) metadata.putAll(details);
        activity.setMetadata(metadata);
        activity.setCreatedAt(OffsetDateTime.now());
        activityRepository.save(activity);
        if (auditOperations != null) {
            Map<String, Object> auditDetails = new LinkedHashMap<>();
            auditDetails.put("roleId", roleId);
            auditDetails.put("action", action);
            if (details != null) auditDetails.putAll(details);
            auditOperations.record(new AuditRecord(
                    normalizeTenant(tenantId),
                    requiredActor(context),
                    "TENANT",
                    normalizeTenant(tenantId),
                    "AUTHORIZATION_ROLE_CHANGED",
                    "ROLE",
                    roleId,
                    AuditOutcome.SUCCESS,
                    context == null ? null : context.requestId(),
                    context == null ? null : context.requestId(),
                    null,
                    auditDetails,
                    idempotencyKey("role", tenantId, roleId, action, context, List.of()),
                    OffsetDateTime.now(),
                    null));
        }
    }

    private UserActivity targetActivity(
            User target,
            String roleId,
            String reason,
            long oldVersion,
            long newVersion,
            AuditContext context
    ) {
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setTenantId(target.getTenantId());
        activity.setUserId(requiredActor(context));
        activity.setTargetUserId(target.getId());
        activity.setAction(reason);
        activity.setDescription("User authorization changed");
        activity.setRequestId(context == null ? null : context.requestId());
        activity.setIpAddress(context == null ? null : context.ipAddress());
        activity.setUserAgent(context == null ? null : context.userAgent());
        activity.setMetadata(versionMetadata(roleId, reason, oldVersion, newVersion, context));
        activity.setCreatedAt(OffsetDateTime.now());
        return activity;
    }

    private SecurityEvent versionEvent(
            User target,
            String roleId,
            String reason,
            long oldVersion,
            long newVersion,
            AuditContext context
    ) {
        SecurityEvent event = new SecurityEvent();
        event.setId(IdGenerator.newId());
        event.setTenantId(target.getTenantId());
        event.setUserId(target.getId());
        event.setEventType("AUTHZ_VERSION_CHANGED");
        event.setSeverity("INFO");
        event.setResult("SUCCESS");
        event.setRiskCode("AUTHZ_CHANGED");
        event.setReason(reason);
        event.setMetadata(versionMetadata(roleId, reason, oldVersion, newVersion, context));
        event.setOccurredAt(OffsetDateTime.now());
        return event;
    }

    private Map<String, Object> versionMetadata(
            String roleId,
            String reason,
            long oldVersion,
            long newVersion,
            AuditContext context
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actorUserId", requiredActor(context));
        metadata.put("roleId", roleId);
        metadata.put("oldVersion", oldVersion);
        metadata.put("newVersion", newVersion);
        metadata.put("reason", reason);
        if (context != null && context.requestId() != null) metadata.put("requestId", context.requestId());
        return metadata;
    }

    private String requiredActor(AuditContext context) {
        if (context == null || context.actorUserId() == null || context.actorUserId().isBlank()) {
            throw new IllegalArgumentException("authorization mutation actor is required");
        }
        return context.actorUserId().trim();
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "erg" : tenantId.trim();
    }

    private String idempotencyKey(
            String prefix,
            String tenantId,
            String roleId,
            String action,
            AuditContext context,
            List<String> userIds
    ) {
        String requestId = context == null ? null : context.requestId();
        String stable = String.join(",", userIds == null ? List.of() : userIds);
        String seed = prefix + ":" + normalizeTenant(tenantId) + ":" + roleId + ":" + action + ":" + stable;
        if (requestId != null && !requestId.isBlank()) seed = requestId.trim() + ":" + seed;
        int hash = seed.hashCode();
        return "authz:" + Integer.toUnsignedString(hash, 36) + ":" + Math.abs((long) hash);
    }

    public record UserRef(String userId, String tenantId) {
    }
}
