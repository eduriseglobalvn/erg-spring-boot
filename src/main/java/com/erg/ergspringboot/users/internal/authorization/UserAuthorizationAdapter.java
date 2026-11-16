package com.erg.ergspringboot.users.internal.authorization;

import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.UserEffectivePermissionProjection;
import com.erg.ergspringboot.users.persistence.UserRoleNameProjection;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import com.erg.ergspringboot.users.UserAuthorizationProjection;
import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.EffectivePermissionResolver;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScopeRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** JPA-backed implementation of the users authorization read port. */
@Service
public final class UserAuthorizationAdapter implements UserAuthorizationPort {

    private final UserRepository users;
    private final UserAccessScopeRepository accessScopes;
    private final UserRoleAssignmentRepository assignments;
    private final EducationUnitRepository units;

    public UserAuthorizationAdapter(
            UserRepository users, UserAccessScopeRepository accessScopes,
            UserRoleAssignmentRepository assignments, EducationUnitRepository units) {
        this.users = users;
        this.accessScopes = accessScopes;
        this.assignments = assignments;
        this.units = units;
    }

    @Override
    public Optional<UserAuthorizationProjection> find(String userId, String tenantId) {
        String scopedTenant = normalizeTenant(tenantId);
        return users.findByIdAndTenantId(userId, scopedTenant)
                .map(ignored -> snapshot(userId, scopedTenant));
    }

    @Override
    public Optional<UserAuthorizationProjection> findEffective(String userId, String tenantId) {
        String scopedTenant = normalizeTenant(tenantId);
        if (userId == null) {
            return Optional.empty();
        }
        return users.findByIdAndTenantId(userId, scopedTenant).map(user -> {
            OffsetDateTime now = OffsetDateTime.now();
            List<UserRoleAssignment> active = assignments.findActiveForUser(userId, scopedTenant, now);
            Set<String> roleNames = new LinkedHashSet<>();
            java.util.stream.Stream.concat(
                            user.getRoles() == null ? java.util.stream.Stream.<Role>empty() : user.getRoles().stream(),
                            active.stream().map(UserRoleAssignment::getRole))
                    .filter(java.util.Objects::nonNull)
                    .map(Role::getName)
                    .filter(this::nonBlank)
                    .map(name -> name.trim().toLowerCase(java.util.Locale.ROOT))
                    .forEach(roleNames::add);
            EffectivePermissionResolver.Result resolved = EffectivePermissionResolver.resolve(user, active, now);
            return new UserAuthorizationProjection(userId, scopedTenant, List.copyOf(roleNames), resolved.granted(),
                    List.of(), resolved.denied());
        });
    }

    @Override
    public List<String> managedEducationUnitIds(String userId, String tenantId) {
        String scopedTenant = normalizeTenant(tenantId);
        if (userId == null || users.findByIdAndTenantId(userId, scopedTenant).isEmpty()) {
            return List.of();
        }
        // Same roots as SessionUnitScopeResolver: LMS access scopes plus active education-unit-scoped assignments.
        Set<String> roots = new LinkedHashSet<>();
        accessScopes.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(AccessScopeAssembler::appliesToLms)
                .map(scope -> scope.getEducationUnitId())
                .filter(this::nonBlank)
                .forEach(roots::add);
        assignments.findActiveForUser(userId, scopedTenant, OffsetDateTime.now()).stream()
                .filter(assignment -> AccessScopeAssembler.isEducationUnitScopeType(assignment.getScopeType()))
                .map(assignment -> assignment.getScopeId())
                .filter(this::nonBlank)
                .forEach(roots::add);
        Set<String> managed = new LinkedHashSet<>();
        if (!roots.isEmpty()) {
            units.findByIdInOrderByNameAsc(roots).stream()
                    .filter(unit -> scopedTenant.equals(unit.getTenantId()))
                    .map(EducationUnit::getId)
                    .forEach(managed::add);
        }
        Set<String> frontier = new LinkedHashSet<>(managed);
        while (!frontier.isEmpty()) {
            Set<String> next = new LinkedHashSet<>();
            for (EducationUnit child : units.findByTenantIdAndParentIdInOrderByNameAsc(
                    scopedTenant, frontier, Pageable.unpaged())) {
                if (managed.add(child.getId())) {
                    next.add(child.getId());
                }
            }
            frontier = next;
        }
        return List.copyOf(managed);
    }

    private UserAuthorizationProjection snapshot(String userId, String tenantId) {
        List<String> roleNames = users.findRoleNamesByUserIds(List.of(userId)).stream()
                .map(UserRoleNameProjection::roleName)
                .filter(this::nonBlank)
                .distinct()
                .toList();
        List<String> rolePermissions = users.findRoleEffectivePermissionsByUserIds(List.of(userId)).stream()
                .filter(row -> row.source() != null && row.source().startsWith("role:"))
                .map(UserEffectivePermissionProjection::permission)
                .filter(this::nonBlank)
                .distinct()
                .toList();
        // Per-user overrides are retired: access comes from business roles only (same as findEffective).
        return new UserAuthorizationProjection(userId, tenantId, roleNames, rolePermissions, List.of(), List.of());
    }

    private String normalizeTenant(String value) {
        return value == null || value.isBlank() ? "erg" : value.trim();
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
