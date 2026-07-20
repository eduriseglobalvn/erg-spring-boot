package com.erg.ergspringboot.service.authorization;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AssignScopedRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RevokeScopedRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.ScopedRoleAssignmentResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.ProtectedRolePolicy;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService.UserRef;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScopedRoleAssignmentService {
    private static final List<String> SCOPE_TYPES = List.of("SYSTEM", "TENANT", "CENTER", "SCHOOL", "CLASS");
    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuthorizationMutationAuditService mutationAuditService;
    private final ProtectedRolePolicy protectedRolePolicy = new ProtectedRolePolicy();

    public ScopedRoleAssignmentService(
            UserRoleAssignmentRepository assignmentRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            AuthorizationMutationAuditService mutationAuditService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.mutationAuditService = mutationAuditService;
    }

    @Transactional
    public ScopedRoleAssignmentResponse assign(
            String userId,
            String tenantId,
            AssignScopedRoleRequest request,
            ErgPrincipal actor
    ) {
        return assign(userId, tenantId, request, actor, actorContext(actor));
    }

    @Transactional
    public ScopedRoleAssignmentResponse assign(
            String userId,
            String tenantId,
            AssignScopedRoleRequest request,
            ErgPrincipal actor,
            AuditContext auditContext
    ) {
        String normalizedTenant = normalizeTenant(tenantId);
        User user = userRepository.findByIdAndTenantId(userId, normalizedTenant)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        Role role = roleRepository.findById(request.roleId())
                .or(() -> roleRepository.findByNameIgnoreCase(request.roleId()))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        protectedRolePolicy.assertCanGrant(actor, List.of(role));
        String scopeType = request.scopeType().trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_TYPES.contains(scopeType)) throw new BadRequestException("invalid role assignment scope");
        String scopeId = trimToNull(request.scopeId());
        if (!List.of("SYSTEM", "TENANT").contains(scopeType) && scopeId == null) {
            throw new BadRequestException("scope id is required");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("role assignment expiry must be in the future");
        }
        AuditContext context = auditContext == null ? actorContext(actor) : auditContext;
        if (context.actorUserId() == null || context.actorUserId().isBlank()) {
            throw new BadRequestException("assignment actor is required");
        }
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setId(IdGenerator.newId());
        assignment.setUserId(user.getId());
        assignment.setRoleId(role.getId());
        assignment.setTenantId(normalizedTenant);
        assignment.setScopeType(scopeType);
        assignment.setScopeId(scopeId);
        assignment.setExpiresAt(request.expiresAt());
        assignment.setAssignedBy(context.actorUserId());
        assignment = assignmentRepository.save(assignment);
        mutationAuditService.authorizationChanged(
                List.of(new UserRef(user.getId(), normalizedTenant)),
                role.getId(),
                "SCOPED_ROLE_ASSIGNED",
                context
        );
        return toResponse(assignment, role);
    }

    @Transactional
    public ScopedRoleAssignmentResponse revoke(
            String userId,
            String assignmentId,
            String tenantId,
            RevokeScopedRoleRequest request,
            ErgPrincipal actor,
            AuditContext auditContext
    ) {
        String normalizedTenant = normalizeTenant(tenantId);
        userRepository.findByIdAndTenantId(userId, normalizedTenant)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        UserRoleAssignment assignment = assignmentRepository.findWithRoleById(assignmentId)
                .filter(item -> userId.equals(item.getUserId()) && normalizedTenant.equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("role assignment not found"));
        Role role = assignment.getRole();
        if (role == null) {
            role = roleRepository.findById(assignment.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        }
        if (assignment.getRevokedAt() != null) {
            return toResponse(assignment, role);
        }
        AuditContext context = auditContext == null ? actorContext(actor) : auditContext;
        if (context.actorUserId() == null || context.actorUserId().isBlank()) {
            throw new BadRequestException("revocation actor is required");
        }
        assignment.setRevokedAt(OffsetDateTime.now());
        assignment.setRevokedBy(context.actorUserId());
        assignment.setRevokeReason(revokeReason(request));
        assignment = assignmentRepository.save(assignment);
        mutationAuditService.authorizationChanged(
                List.of(new UserRef(userId, normalizedTenant)),
                role.getId(),
                "SCOPED_ROLE_REVOKED",
                context
        );
        return toResponse(assignment, role);
    }

    private ScopedRoleAssignmentResponse toResponse(UserRoleAssignment assignment, Role role) {
        return new ScopedRoleAssignmentResponse(
                assignment.getId(), assignment.getUserId(), assignment.getRoleId(), role.getName(),
                assignment.getTenantId(), assignment.getScopeType(), assignment.getScopeId(),
                assignment.getExpiresAt(), assignment.getAssignedBy(), assignment.getCreatedAt(),
                assignment.getRevokedAt(), assignment.getRevokedBy(), assignment.getRevokeReason()
        );
    }

    private AuditContext actorContext(ErgPrincipal actor) {
        return new AuditContext(actor == null ? null : actor.userId(), null, null, null);
    }

    private String revokeReason(RevokeScopedRoleRequest request) {
        String reason = request == null ? null : trimToNull(request.reason());
        return reason == null ? "revoked by administrator" : reason;
    }

    private String normalizeTenant(String value) {
        return value == null || value.isBlank() ? "erg" : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
