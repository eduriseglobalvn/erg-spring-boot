package com.erg.ergspringboot.service.sessions;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapPermissions;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapClass;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapScope;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapUnit;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapUser;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionContextResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final String DEFAULT_TENANT = "erg";
    private static final Set<String> SYSTEM_ADMIN_ROLES = Set.of("system.super_admin", "erg_super_admin", "super_admin", "admin");

    private final AuthSessionRepository sessionRepository;
    private final EducationUnitRepository educationUnitRepository;
    private final ClassroomRepository classroomRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;

    public SessionService(
            AuthSessionRepository sessionRepository,
            EducationUnitRepository educationUnitRepository,
            ClassroomRepository classroomRepository,
            UserPermissionOverrideRepository userPermissionOverrideRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.educationUnitRepository = educationUnitRepository;
        this.classroomRepository = classroomRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
    }

    @Transactional(readOnly = true)
    public SessionContextResponse currentSession(String sessionId, String tenantId) {
        AuthSession session = sessionRepository.findBySessionIdAndTenantId(sessionId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        boolean active = session.getRevokedAt() == null;
        List<String> roles = session.getUser() == null || session.getUser().getRoles() == null
                ? List.of()
                : session.getUser().getRoles().stream().map(Role::getName).toList();
        return new SessionContextResponse(
                session.getSessionId(),
                session.getUserId(),
                session.getTenantId(),
                session.getDeviceId(),
                session.getIpAddress(),
                active,
                roles,
                session.getExpiresAt() == null ? null : session.getExpiresAt().toString(),
                session.getLastActiveAt() == null ? null : session.getLastActiveAt().toString()
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "sessionBootstrap",
            key = "{#sessionId, #tenantId, #requestedAccess.currentEducationUnitId()}"
    )
    public SessionBootstrapResponse currentBootstrap(String sessionId, String tenantId, UserAccessContext requestedAccess) {
        AuthSession session = sessionRepository.findWithUserBySessionIdAndTenantId(sessionId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        User user = session.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("session user not found");
        }

        List<String> roles = roleNames(user);
        PermissionEnvelope permissionEnvelope = permissionEnvelope(user);
        boolean canAccessGlobalErg = isSystemAdmin(roles) || permissionEnvelope.grantedPermissions().contains("*");

        List<ManagedUnit> managedUnits = resolveManageableUnits(user, roles, canAccessGlobalErg, normalizeTenant(tenantId));
        List<SessionBootstrapUnit> manageableUnits = toBootstrapUnits(managedUnits);
        List<SessionBootstrapClass> manageableClasses = resolveManageableClasses(normalizeTenant(tenantId), managedUnits);

        SessionBootstrapScope currentScope = resolveCurrentScope(
                requestedAccess,
                managedUnits,
                manageableClasses,
                canAccessGlobalErg
        );

        return new SessionBootstrapResponse(
                new SessionBootstrapUser(user.getId(), user.getFullName(), user.getEmail(), user.getAvatarUrl()),
                new SessionBootstrapPermissions(
                        canAccessGlobalErg,
                        roles,
                        permissionEnvelope.grantedPermissions(),
                        permissionEnvelope.deniedPermissions()
                ),
                currentScope,
                manageableUnits,
                manageableClasses
        );
    }

    private List<ManagedUnit> resolveManageableUnits(
            User user,
            List<String> roles,
            boolean canAccessGlobalErg,
            String tenantId
    ) {
        List<EducationUnit> educationUnits = canAccessGlobalErg
                ? educationUnitRepository.findAllByOrderByNameAsc()
                : scopedEducationUnits(user, roles, tenantId);
        return educationUnits.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(this::unitSortKey).thenComparing(unit -> safe(unit.getName())))
                .map(unit -> new ManagedUnit(unit.getId(), normalizedUnitType(unit), unit.getName(), unit.getLogoUrl()))
                .toList();
    }

    private List<EducationUnit> scopedEducationUnits(User user, List<String> roles, String tenantId) {
        List<UserAccessScope> scopes = accessScopes(user).stream()
                .filter(this::appliesToLms)
                .toList();
        Set<String> directIds = new LinkedHashSet<>();
        Set<String> educationUnitAdminRootIds = new LinkedHashSet<>();

        for (UserAccessScope scope : scopes) {
            String educationUnitId = safe(scope.getEducationUnitId());
            if (educationUnitId.isBlank()) {
                continue;
            }
            directIds.add(educationUnitId);
            if (isEducationUnitAdminScope(scope, roles)) {
                educationUnitAdminRootIds.add(educationUnitId);
            }
        }

        if (isTeacherLike(roles)) {
            directIds.addAll(classroomRepository.findTeacherManagedSchoolIds(tenantId, user.getId()));
        }

        Map<String, EducationUnit> educationUnitsById = new LinkedHashMap<>();
        findEducationUnitsByIds(directIds).forEach(unit -> educationUnitsById.put(unit.getId(), unit));
        descendantEducationUnits(educationUnitAdminRootIds).forEach(unit -> educationUnitsById.putIfAbsent(unit.getId(), unit));
        return List.copyOf(educationUnitsById.values());
    }

    private List<EducationUnit> findEducationUnitsByIds(Set<String> ids) {
        return ids.isEmpty() ? List.of() : educationUnitRepository.findByIdInOrderByNameAsc(ids);
    }

    private List<EducationUnit> descendantEducationUnits(Set<String> rootIds) {
        Set<String> seenParentIds = new LinkedHashSet<>(rootIds);
        Set<String> frontier = new LinkedHashSet<>(rootIds);
        Map<String, EducationUnit> descendants = new LinkedHashMap<>();

        while (!frontier.isEmpty()) {
            List<EducationUnit> children = educationUnitRepository.findByParentIdInOrderByNameAsc(frontier);
            frontier = new LinkedHashSet<>();
            for (EducationUnit child : children) {
                descendants.putIfAbsent(child.getId(), child);
                if (seenParentIds.add(child.getId())) {
                    frontier.add(child.getId());
                }
            }
        }
        return List.copyOf(descendants.values());
    }

    private List<SessionBootstrapUnit> toBootstrapUnits(List<ManagedUnit> managedUnits) {
        return managedUnits.stream()
                .map(unit -> new SessionBootstrapUnit(
                        unit.id(),
                        unit.name(),
                        unit.avatar(),
                        unit.type(),
                        levelFromUnitType(unit.type())
                ))
                .toList();
    }

    private List<SessionBootstrapClass> resolveManageableClasses(String tenantId, List<ManagedUnit> managedUnits) {
        List<String> schoolIds = managedUnits.stream()
                .map(ManagedUnit::id)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (schoolIds.isEmpty()) {
            return List.of();
        }

        return classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(tenantId, schoolIds).stream()
                .map(this::toBootstrapClass)
                .toList();
    }

    private SessionBootstrapClass toBootstrapClass(Classroom classroom) {
        return new SessionBootstrapClass(
                classroom.getId(),
                classroom.getName(),
                classroom.getSchoolId(),
                classroom.getGrade(),
                classroom.getAcademicYear(),
                classroom.getStatus()
        );
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId.trim();
    }

    private List<String> roleNames(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return List.of("user");
        }
        return user.getRoles().stream()
                .map(Role::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    private PermissionEnvelope permissionEnvelope(User user) {
        Set<String> granted = new LinkedHashSet<>();
        Set<String> denied = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.getPermissions() == null) {
                    continue;
                }
                for (Permission permission : role.getPermissions()) {
                    if (permission != null && permission.getName() != null && !permission.getName().isBlank()) {
                        granted.add(permission.getName().trim());
                    }
                }
            }
        }
        Collection<UserPermissionOverride> overrides = user.getPermissionOverrides() == null
                ? userPermissionOverrideRepository.findByUserId(user.getId())
                : user.getPermissionOverrides();
        for (UserPermissionOverride override : overrides) {
            if (override == null || override.getPermission() == null || override.getPermission().isBlank()) {
                continue;
            }
            if (override.getExpiresAt() != null && override.getExpiresAt().isBefore(OffsetDateTime.now())) {
                continue;
            }
            String permission = override.getPermission().trim();
            if (isGrantOverride(override)) {
                granted.add(permission);
                denied.remove(permission);
            } else {
                denied.add(permission);
                granted.remove(permission);
            }
        }
        return new PermissionEnvelope(List.copyOf(granted), List.copyOf(denied));
    }

    private boolean isGrantOverride(UserPermissionOverride override) {
        String grantType = safe(override.getGrantType()).toLowerCase(Locale.ROOT);
        return grantType.isBlank() || grantType.equals("grant") || grantType.equals("allow");
    }

    private boolean appliesToLms(UserAccessScope scope) {
        String modules = safe(scope.getModules()).toLowerCase(Locale.ROOT);
        return modules.isBlank() || modules.equals("*") || modules.contains("lms");
    }

    private List<UserAccessScope> accessScopes(User user) {
        if (user.getAccessScopes() == null || user.getAccessScopes().isEmpty()) {
            return List.of();
        }
        return user.getAccessScopes().stream()
                .sorted(Comparator.comparing(UserAccessScope::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean isEducationUnitAdminScope(UserAccessScope scope, List<String> roleNames) {
        String scopedRole = safe(scope.getRole()).toLowerCase(Locale.ROOT);
        if (scopedRole.contains("center_admin")) {
            return true;
        }
        return roleNames.stream().map(role -> role.toLowerCase(Locale.ROOT)).anyMatch(role ->
                role.contains("center_admin") || role.equals("lms.admin"));
    }

    private boolean isTeacherLike(List<String> roleNames) {
        return roleNames.stream()
                .map(role -> role.toLowerCase(Locale.ROOT))
                .anyMatch(role -> role.contains("teacher") || role.contains("instructor"));
    }

    private boolean isSystemAdmin(List<String> roles) {
        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .anyMatch(SYSTEM_ADMIN_ROLES::contains);
    }

    private SessionBootstrapScope resolveCurrentScope(
            UserAccessContext requestedAccess,
            List<ManagedUnit> manageableUnits,
            List<SessionBootstrapClass> manageableClasses,
            boolean canAccessGlobalErg
    ) {
        if (requestedAccess.currentClassId() != null && !requestedAccess.currentClassId().isBlank()) {
            return manageableClasses.stream()
                    .filter(classroom -> classroom.id().equals(requestedAccess.currentClassId()))
                    .findFirst()
                    .map(classroom -> new SessionBootstrapScope("class", classroom.unitId(), classroom.id()))
                    .orElseGet(() -> fallbackScope(manageableUnits, canAccessGlobalErg));
        }

        if (requestedAccess.currentEducationUnitId() != null && !requestedAccess.currentEducationUnitId().isBlank()) {
            return manageableUnits.stream()
                    .filter(unit -> unit.id().equals(requestedAccess.currentEducationUnitId()))
                    .findFirst()
                    .map(unit -> new SessionBootstrapScope(levelFromUnitType(unit.type()), unit.id(), null))
                    .orElseGet(() -> fallbackScope(manageableUnits, canAccessGlobalErg));
        }
        return fallbackScope(manageableUnits, canAccessGlobalErg);
    }

    private SessionBootstrapScope fallbackScope(
            List<ManagedUnit> manageableUnits,
            boolean canAccessGlobalErg
    ) {
        if (!manageableUnits.isEmpty()) {
            ManagedUnit unit = manageableUnits.getFirst();
            return new SessionBootstrapScope(levelFromUnitType(unit.type()), unit.id(), null);
        }
        if (canAccessGlobalErg) {
            return new SessionBootstrapScope("system", "system", null);
        }
        return new SessionBootstrapScope("none", null, null);
    }

    private String normalizedUnitType(EducationUnit educationUnit) {
        String type = safe(educationUnit.getType()).toLowerCase(Locale.ROOT);
        if (type.contains("school") || type.contains("campus")) {
            return "school";
        }
        if (type.contains("system")) {
            return "system";
        }
        return "center";
    }

    private String levelFromUnitType(String type) {
        String normalized = safe(type).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "school" -> "school";
            case "system" -> "system";
            default -> "center";
        };
    }

    private int unitSortKey(EducationUnit educationUnit) {
        return switch (normalizedUnitType(educationUnit)) {
            case "system" -> 0;
            case "center" -> 1;
            default -> 2;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record PermissionEnvelope(List<String> grantedPermissions, List<String> deniedPermissions) {
    }

    private record ManagedUnit(String id, String type, String name, String avatar) {
    }
}
