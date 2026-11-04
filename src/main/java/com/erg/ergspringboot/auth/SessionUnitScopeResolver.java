package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapClass;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapScope;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapUnit;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScope;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;

/**
 * Which education units and classes a session may manage, and which of them is the current scope.
 * Plain collaborator of {@link SessionService}; it runs inside the caller's read-only transaction.
 */
final class SessionUnitScopeResolver {

    private static final int MAX_BOOTSTRAP_UNITS = 500;
    private static final int MAX_BOOTSTRAP_CLASSES = 500;
    private static final Set<String> EDUCATION_UNIT_ADMIN_ROLES = Set.of(
            "center_admin", "school_admin", "principal", "lms.admin");

    private final EducationUnitRepository educationUnitRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    SessionUnitScopeResolver(
            EducationUnitRepository educationUnitRepository,
            ClassroomRepository classroomRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this.educationUnitRepository = educationUnitRepository;
        this.classroomRepository = classroomRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    List<ManagedUnit> resolveManageableUnits(
            User user,
            List<String> roles,
            boolean canAccessGlobalErg,
            String tenantId
    ) {
        List<EducationUnit> educationUnits = canAccessGlobalErg
                ? educationUnitRepository.findTop500ByOrderByNameAsc()
                : scopedEducationUnits(user, roles, tenantId);
        return educationUnits.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(this::unitSortKey).thenComparing(unit -> safe(unit.getName())))
                .map(unit -> new ManagedUnit(unit.getId(), normalizedUnitType(unit), unit.getName(), unit.getLogoUrl()))
                .toList();
    }

    List<SessionBootstrapUnit> toBootstrapUnits(List<ManagedUnit> managedUnits) {
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

    List<SessionBootstrapClass> resolveManageableClasses(String tenantId, List<ManagedUnit> managedUnits) {
        List<String> schoolIds = managedUnits.stream()
                .map(ManagedUnit::id)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (schoolIds.isEmpty()) {
            return List.of();
        }

        List<Classroom> classes = classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(
                tenantId, schoolIds, PageRequest.of(0, MAX_BOOTSTRAP_CLASSES + 1));
        requireWithinLimit(classes.size(), MAX_BOOTSTRAP_CLASSES, "classes");
        return classes.stream()
                .map(this::toBootstrapClass)
                .toList();
    }

    SessionBootstrapScope resolveCurrentScope(
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

    private List<EducationUnit> scopedEducationUnits(User user, List<String> roles, String tenantId) {
        List<UserAccessScope> scopes = accessScopes(user).stream()
                .filter(AccessScopeAssembler::appliesToLms)
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

        if (user != null && user.getId() != null) {
            List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findActiveForUser(
                    user.getId(), tenantId, OffsetDateTime.now());
            for (UserRoleAssignment assignment : assignments) {
                String scopeId = safe(assignment.getScopeId());
                String scopeType = safe(assignment.getScopeType()).toUpperCase(Locale.ROOT);
                if (scopeId.isBlank()) {
                    continue;
                }
                if (AccessScopeAssembler.isEducationUnitScopeType(scopeType)) {
                    directIds.add(scopeId);
                    if (isEducationUnitAdminRole(assignment) || isEducationUnitAdminRoles(roles)) {
                        educationUnitAdminRootIds.add(scopeId);
                    }
                }
            }
        }

        if (isTeacherLike(roles)) {
            directIds.addAll(classroomRepository.findTeacherManagedSchoolIds(tenantId, user.getId()));
        }

        Map<String, EducationUnit> educationUnitsById = new LinkedHashMap<>();
        findEducationUnitsByIds(directIds).forEach(unit -> educationUnitsById.put(unit.getId(), unit));
        descendantEducationUnits(educationUnitAdminRootIds, tenantId)
                .forEach(unit -> educationUnitsById.putIfAbsent(unit.getId(), unit));
        requireWithinLimit(educationUnitsById.size(), MAX_BOOTSTRAP_UNITS, "education units");
        return List.copyOf(educationUnitsById.values());
    }

    private List<EducationUnit> findEducationUnitsByIds(Set<String> ids) {
        requireWithinLimit(ids.size(), MAX_BOOTSTRAP_UNITS, "education units");
        return ids.isEmpty() ? List.of() : educationUnitRepository.findByIdInOrderByNameAsc(ids);
    }

    private List<EducationUnit> descendantEducationUnits(Set<String> rootIds, String tenantId) {
        Set<String> seenParentIds = new LinkedHashSet<>(rootIds);
        Set<String> frontier = new LinkedHashSet<>(rootIds);
        Map<String, EducationUnit> descendants = new LinkedHashMap<>();

        while (!frontier.isEmpty()) {
            List<EducationUnit> children = educationUnitRepository.findByTenantIdAndParentIdInOrderByNameAsc(
                    tenantId, frontier, PageRequest.of(0, MAX_BOOTSTRAP_UNITS + 1));
            requireWithinLimit(descendants.size() + children.size(), MAX_BOOTSTRAP_UNITS, "education units");
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

    private void requireWithinLimit(int actual, int maximum, String section) {
        if (actual > maximum) {
            throw new BadRequestException("session bootstrap " + section + " exceeds " + maximum
                    + " rows; select a narrower scope");
        }
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

    private List<UserAccessScope> accessScopes(User user) {
        if (user.getAccessScopes() == null || user.getAccessScopes().isEmpty()) {
            return List.of();
        }
        return user.getAccessScopes().stream()
                .sorted(Comparator.comparing(UserAccessScope::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean isEducationUnitAdminScope(UserAccessScope scope, List<String> roleNames) {
        return isEducationUnitAdminRoleName(scope.getRole()) || isEducationUnitAdminRoles(roleNames);
    }

    private boolean isEducationUnitAdminRole(UserRoleAssignment assignment) {
        String roleName = assignment.getRole() == null ? null : assignment.getRole().getName();
        return isEducationUnitAdminRoleName(roleName) || isEducationUnitAdminRoleName(assignment.getRoleId());
    }

    private boolean isEducationUnitAdminRoles(List<String> roleNames) {
        return roleNames.stream().anyMatch(this::isEducationUnitAdminRoleName);
    }

    /** Exact role names only: "vice_principal" or "school_admin_assistant" must not inherit unit-admin scope. */
    private boolean isEducationUnitAdminRoleName(String roleName) {
        return EDUCATION_UNIT_ADMIN_ROLES.contains(safe(roleName).trim().toLowerCase(Locale.ROOT));
    }

    private boolean isTeacherLike(List<String> roleNames) {
        return roleNames.stream()
                .map(role -> role.toLowerCase(Locale.ROOT))
                .anyMatch(role -> role.contains("teacher") || role.contains("instructor"));
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
        if (type.contains("school") || type.contains("campus")
                || type.contains("primary") || type.contains("secondary")
                || type.contains("high") || type.contains("k12") || type.contains("kindergarten")) {
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

    record ManagedUnit(String id, String type, String name, String avatar) {
    }
}
