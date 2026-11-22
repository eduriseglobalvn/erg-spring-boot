package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.authorization.catalog.BusinessRole;
import com.erg.ergspringboot.authorization.catalog.BusinessRoleCatalog;
import com.erg.ergspringboot.authorization.catalog.PermissionCatalog;
import com.erg.ergspringboot.authorization.catalog.PermissionKind;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.RoleRepository;
import com.erg.ergspringboot.users.persistence.authorization.RolePermissionIndex;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single "never beyond your own authority" rule for every action on another user's account:
 * the target is not the actor, every effective permission of the target (before and after) and of every
 * role granted or revoked lies within the actor's permissions, and the school scope lies within the actor's.
 * A platform-wide actor (verified system role plus wildcard) is a superset of everything.
 */
@Component
public class AccountActionGuard {

    public enum AccountAction {
        ASSIGN_ROLE, REVOKE_ROLE, REPLACE_ROLES, RESET_PASSWORD, RESEND_INVITE, LOCK, UNLOCK, DELETE,
        REVOKE_SESSIONS, EDIT_EMPLOYEE
    }

    /** {@code code} is roles.name, the value the user API takes; label and exclusive come from the catalog. */
    public record AssignableRole(String id, String code, String label, boolean exclusive) {
    }

    public static final String DENIED_MESSAGE = "Bạn không có quyền thực hiện thao tác này";
    public static final int BULK_LIMIT = 100;
    static final String SUPER_ADMIN_ROLE_ID = "role_super_admin";
    static final Set<String> EXCLUSIVE_ROLE_IDS = BusinessRoleCatalog.all().stream()
            .filter(BusinessRole::exclusive).map(BusinessRole::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> UNIT_SCOPE_TYPES = Set.of("CENTER", "SCHOOL", "CLASS");
    private static final Set<AccountAction> REMOVES_ACCESS = Set.of(AccountAction.LOCK, AccountAction.DELETE);

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleAssignmentRepository assignments;
    private RolePermissionIndex rolePermissions;
    private final AuthorizationCapabilityPolicy capabilityPolicy = new AuthorizationCapabilityPolicy();

    public AccountActionGuard(UserRepository users, RoleRepository roles, UserRoleAssignmentRepository assignments) {
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.rolePermissions = new RolePermissionIndex(roles);
    }

    /** The cached index; the constructor default reads uncached (unit tests without a Spring context). */
    @org.springframework.beans.factory.annotation.Autowired
    void setRolePermissionIndex(RolePermissionIndex rolePermissions) {
        this.rolePermissions = rolePermissions;
    }

    @Transactional(readOnly = true)
    public void assertCanActOn(ErgPrincipal actor, String targetUserId, AccountAction action) {
        User target = load(actor, targetUserId);
        OffsetDateTime now = OffsetDateTime.now();
        check(actor, target, action, assignments.findActiveForUser(target.getId(), target.getTenantId(), now), now);
    }

    private void check(ErgPrincipal actor, User target, AccountAction action, List<UserRoleAssignment> active,
            OffsetDateTime now) {
        assertNotSelf(actor, target);
        assertWithinActor(actor, new TreeSet<>(EffectivePermissionResolver.resolve(target, active, now).granted()));
        if (REMOVES_ACCESS.contains(action) && roleIds(target, active).contains(SUPER_ADMIN_ROLE_ID)) {
            assertAnotherActiveSuperAdmin(actor, target);
        }
    }

    /** Checks every target first and the batch size, so a batch is rejected whole or not at all. */
    @Transactional(readOnly = true)
    public void assertCanActOnAll(ErgPrincipal actor, Collection<String> targetUserIds, AccountAction action) {
        Set<String> ids = new LinkedHashSet<>(targetUserIds == null ? List.of() : targetUserIds);
        if (ids.size() > BULK_LIMIT) {
            throw new BadRequestException("Tối đa " + BULK_LIMIT + " tài khoản mỗi lần");
        }
        if (ids.isEmpty()) return;
        requireActor(actor);
        // Same decisions as assertCanActOn per target, from one user query and one assignment query.
        Map<String, User> targets = users.findWithAuthorityByTenantIdAndIdIn(actor.tenantId(), ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, List<UserRoleAssignment>> held = assignments.findActiveForUsers(actor.tenantId(), ids, now).stream()
                .collect(Collectors.groupingBy(UserRoleAssignment::getUserId));
        for (String id : ids) {
            User target = targets.get(id);
            if (target == null) throw new ResourceNotFoundException("user not found");
            check(actor, target, action, held.getOrDefault(id, List.of()), now);
        }
    }

    /** {@code roleIdsAfter} is the full set of role ids the target would hold after the change. */
    @Transactional(readOnly = true)
    public void assertCanAssign(ErgPrincipal actor, String targetUserId, Set<String> roleIdsAfter,
            String scopeType, String scopeId) {
        User target = load(actor, targetUserId);
        OffsetDateTime now = OffsetDateTime.now();
        List<UserRoleAssignment> held = assignments.findActiveForUser(target.getId(), target.getTenantId(), now);
        Set<String> after = roleIdsAfter == null ? Set.of() : roleIdsAfter;
        checkAssign(actor, target, held, now, after, roles.findAllById(after), scopeType, scopeId);
    }

    /**
     * Adding {@code roleId} to every target: the same decisions as {@link #assertCanAssign} per target (roles held now
     * plus the new one), from one user, one assignment and one role query. The batch is rejected whole or not at all.
     */
    @Transactional(readOnly = true)
    public void assertCanAssignAll(ErgPrincipal actor, Collection<String> targetUserIds, String roleId) {
        Set<String> ids = new LinkedHashSet<>(targetUserIds == null ? List.of() : targetUserIds);
        if (ids.size() > BULK_LIMIT) {
            throw new BadRequestException("Tối đa " + BULK_LIMIT + " tài khoản mỗi lần");
        }
        if (ids.isEmpty()) return;
        requireActor(actor);
        Map<String, User> targets = users.findWithAuthorityByTenantIdAndIdIn(actor.tenantId(), ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, List<UserRoleAssignment>> held = assignments.findActiveForUsers(actor.tenantId(), ids, now).stream()
                .collect(Collectors.groupingBy(UserRoleAssignment::getUserId));
        Map<String, Set<String>> afterByTarget = new java.util.LinkedHashMap<>();
        Set<String> allRoleIds = new LinkedHashSet<>();
        for (String id : ids) {
            User target = targets.get(id);
            if (target == null) throw new ResourceNotFoundException("user not found");
            Set<String> after = new LinkedHashSet<>(roleIds(target, held.getOrDefault(id, List.of())));
            after.add(roleId);
            afterByTarget.put(id, after);
            allRoleIds.addAll(after);
        }
        Map<String, Role> rolesById = roles.findAllById(allRoleIds).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity(), (a, b) -> a));
        for (String id : ids) {
            Set<String> after = afterByTarget.get(id);
            List<Role> afterRoles = after.stream().map(rolesById::get).filter(Objects::nonNull).toList();
            checkAssign(actor, targets.get(id), held.getOrDefault(id, List.of()), now, after, afterRoles, null, null);
        }
    }

    private void checkAssign(ErgPrincipal actor, User target, List<UserRoleAssignment> held, OffsetDateTime now,
            Set<String> after, List<Role> afterRoles, String scopeType, String scopeId) {
        assertNotSelf(actor, target);
        assertWithinActor(actor, new TreeSet<>(EffectivePermissionResolver.resolve(target, held, now).granted()));
        if (afterRoles.size() != after.size()) {
            throw new BadRequestException("one or more roles were not found");
        }
        afterRoles.forEach(role -> assertWithinActor(actor, permissionNames(role)));
        assertRoleCombination(after);
        String type = scopeType == null ? "" : scopeType.trim().toUpperCase(Locale.ROOT);
        if (UNIT_SCOPE_TYPES.contains(type) && scopeId != null && !scopeId.isBlank()
                && !capabilityPolicy.canAccessScope(actor, type, scopeId.trim())) {
            throw new ForbiddenException(DENIED_MESSAGE);
        }
        if (roleIds(target, held).contains(SUPER_ADMIN_ROLE_ID) && !after.contains(SUPER_ADMIN_ROLE_ID)) {
            assertAnotherActiveSuperAdmin(actor, target);
        }
    }

    public static void assertRoleCombination(Collection<String> roleIds) {
        if (roleIds.size() > 1 && roleIds.stream().filter(Objects::nonNull).anyMatch(EXCLUSIVE_ROLE_IDS::contains)) {
            throw new BadRequestException("Vai trò Giám đốc và Vận hành không được kèm vai trò khác");
        }
        if (roleIds.size() > 1 && roleIds.contains(BusinessRoleCatalog.STUDENT)) {
            throw new BadRequestException("Vai trò Học sinh không được kèm vai trò nhân sự");
        }
    }

    /** Role ids the user holds now: direct roles plus active scoped assignments. */
    @Transactional(readOnly = true)
    public Set<String> currentRoleIds(ErgPrincipal actor, String targetUserId) {
        return currentRoleIds(load(actor, targetUserId));
    }

    /** Roles whose whole permission set lies within the caller's own permissions. */
    @Transactional(readOnly = true)
    public List<AssignableRole> assignableRoles(ErgPrincipal actor) {
        return rolePermissions.entries().stream()
                .filter(role -> withinActor(actor, role.permissionNames()))
                .map(role -> {
                    BusinessRole meta = BusinessRoleCatalog.all().stream()
                            .filter(r -> r.id().equals(role.id())).findFirst().orElse(null);
                    return new AssignableRole(role.id(), role.name(),
                            meta == null ? role.name() : meta.label(), meta != null && meta.exclusive());
                })
                .toList();
    }

    private static void requireActor(ErgPrincipal actor) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank()) {
            throw new ForbiddenException(DENIED_MESSAGE);
        }
    }

    private User load(ErgPrincipal actor, String targetUserId) {
        requireActor(actor);
        return users.findByIdAndTenantId(targetUserId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    private void assertNotSelf(ErgPrincipal actor, User target) {
        if (actor.userId().equals(target.getId())) {
            throw new ForbiddenException(DENIED_MESSAGE);
        }
    }

    private Set<String> currentRoleIds(User target) {
        return roleIds(target, assignments.findActiveForUser(target.getId(), target.getTenantId(), OffsetDateTime.now()));
    }

    private static Set<String> roleIds(User target, List<UserRoleAssignment> active) {
        Set<String> ids = new LinkedHashSet<>();
        if (target.getRoles() != null) target.getRoles().stream().map(Role::getId).filter(Objects::nonNull).forEach(ids::add);
        active.stream().map(UserRoleAssignment::getRoleId).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private Set<String> permissionNames(Role role) {
        Set<String> names = new TreeSet<>();
        if (role.getPermissions() != null) {
            role.getPermissions().stream().map(Permission::getName).filter(Objects::nonNull)
                    .map(name -> name.trim().toLowerCase(Locale.ROOT)).forEach(names::add);
        }
        return names;
    }

    private void assertWithinActor(ErgPrincipal actor, Collection<String> permissions) {
        if (!withinActor(actor, permissions)) {
            throw new ForbiddenException(DENIED_MESSAGE);
        }
    }

    private boolean withinActor(ErgPrincipal actor, Collection<String> permissions) {
        if (capabilityPolicy.isPlatformWide(actor)) return true;
        Set<String> have = new LinkedHashSet<>();
        actor.permissions().forEach(p -> have.add(p.trim().toLowerCase(Locale.ROOT)));
        // Portal gates and self-service rights (kind WORKSPACE) are not privileges to escalate: Phòng đào tạo manages
        // teacher and student accounts without holding "lms.quiz.play" itself. "*" never passes.
        return permissions.stream().map(p -> p.trim().toLowerCase(Locale.ROOT))
                .allMatch(p -> !"*".equals(p) && (have.contains(p)
                        || PermissionCatalog.kindOf(p).filter(kind -> kind == PermissionKind.WORKSPACE).isPresent()));
    }

    private void assertAnotherActiveSuperAdmin(ErgPrincipal actor, User target) {
        boolean another = roles.findById(SUPER_ADMIN_ROLE_ID).map(Role::getUsers).stream().flatMap(Set::stream)
                .anyMatch(u -> !u.getId().equals(target.getId()) && "ACTIVE".equalsIgnoreCase(u.getStatus())
                        && Objects.equals(u.getTenantId(), target.getTenantId()));
        if (!another) {
            throw new BadRequestException("Phải còn ít nhất một Quản trị đang hoạt động");
        }
    }
}
