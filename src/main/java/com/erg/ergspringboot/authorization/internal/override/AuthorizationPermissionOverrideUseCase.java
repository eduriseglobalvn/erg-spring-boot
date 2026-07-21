package com.erg.ergspringboot.authorization.internal.override;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AddPermissionOverrideRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionOverrideResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService.UserRef;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User permission override commands, isolated from the authorization facade. */
@Service
public class AuthorizationPermissionOverrideUseCase {
    private final UserRepository users;
    private final PermissionRepository permissions;
    private final UserPermissionOverrideRepository overrides;
    private AuthorizationMutationAuditService audit;

    public AuthorizationPermissionOverrideUseCase(UserRepository users, PermissionRepository permissions,
                                                   UserPermissionOverrideRepository overrides,
                                                   AuthorizationMutationAuditService audit) {
        this.users = users;
        this.permissions = permissions;
        this.overrides = overrides;
        this.audit = audit;
    }

    public void setMutationAuditService(AuthorizationMutationAuditService audit) { this.audit = audit; }

    @Transactional
    public PermissionOverrideResponse add(String userId, AddPermissionOverrideRequest request,
                                          String actorId, String tenantId, AuditContext context) {
        scopedUser(userId, tenantId);
        Permission permission = permissions.findByNameInIgnoreCase(List.of(request.permission()))
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("permission not found"));
        UserPermissionOverride override = new UserPermissionOverride();
        override.setId(IdGenerator.newId());
        override.setUserId(userId);
        override.setPermission(permission.getName());
        override.setGrantType(normalizeGrantType(request.grantType()));
        override.setReason(request.reason());
        override.setExpiresAt(request.expiresAt());
        override.setCreatedBy(actorId);
        override.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        PermissionOverrideResponse response = toResponse(overrides.save(override));
        mutationAudit().authorizationChanged(List.of(new UserRef(userId, normalize(tenantId))), permission.getId(),
                "PERMISSION_OVERRIDE_ADDED", requiredContext(context));
        return response;
    }

    @Transactional
    public void delete(String userId, String overrideId, String tenantId, AuditContext context) {
        scopedUser(userId, tenantId);
        UserPermissionOverride override = overrides.findById(overrideId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("permission override not found"));
        String permissionId = permissions.findByNameInIgnoreCase(List.of(override.getPermission())).stream()
                .findFirst().map(Permission::getId).orElse(override.getPermission());
        overrides.delete(override);
        mutationAudit().authorizationChanged(List.of(new UserRef(userId, normalize(tenantId))), permissionId,
                "PERMISSION_OVERRIDE_DELETED", requiredContext(context));
    }

    private com.erg.ergspringboot.model.users.User scopedUser(String userId, String tenantId) {
        return users.findByIdAndTenantId(userId, normalize(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }
    private PermissionOverrideResponse toResponse(UserPermissionOverride value) {
        return new PermissionOverrideResponse(value.getId(), value.getUserId(), value.getPermission(),
                value.getGrantType(), value.getReason(), value.getExpiresAt(), value.getCreatedBy(), value.getCreatedAt());
    }
    private String normalizeGrantType(String value) {
        String normalized = value.trim().toUpperCase();
        if (!List.of("GRANT", "DENY").contains(normalized)) throw new BadRequestException("invalid permission grant type");
        return normalized;
    }
    private AuthorizationMutationAuditService mutationAudit() {
        if (audit == null) throw new IllegalStateException("authorization mutation audit service is not configured");
        return audit;
    }
    private AuditContext requiredContext(AuditContext context) {
        if (context == null || context.actorUserId() == null || context.actorUserId().isBlank())
            throw new BadRequestException("authorization mutation actor is required");
        return context;
    }
    private String normalize(String value) { return value == null || value.isBlank() ? "erg" : value.trim(); }
}
