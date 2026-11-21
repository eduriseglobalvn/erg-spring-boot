package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapClass;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapScope;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapUnit;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionScopeClasses;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionScopeUnits;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Which education units and classes a session may manage, and which of them is the current scope.
 * Plain collaborator of {@link SessionService}; it runs inside the caller's read-only transaction.
 */
final class SessionUnitScopeResolver {

    private static final Logger log = LoggerFactory.getLogger(SessionUnitScopeResolver.class);
    private static final int MAX_BOOTSTRAP_UNITS = 500;
    private static final int MAX_BOOTSTRAP_CLASSES = 500;

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

    List<ManagedUnit> resolveManageableUnits(ScopeCaller caller) {
        List<EducationUnit> educationUnits = caller.platformWide()
                ? educationUnitRepository.findTop500ByOrderByNameAsc()
                : caller.allUnits()
                        ? educationUnitRepository.findByTenantIdOrderByNameAsc(
                                caller.tenantId(), PageRequest.of(0, MAX_BOOTSTRAP_UNITS))
                        : scopedEducationUnits(caller);
        return sortedUnits(educationUnits);
    }

    private List<ManagedUnit> sortedUnits(List<EducationUnit> educationUnits) {
        return educationUnits.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(this::unitSortKey).thenComparing(unit -> safe(unit.getName())))
                .map(this::toManagedUnit)
                .toList();
    }

    private ManagedUnit toManagedUnit(EducationUnit unit) {
        return new ManagedUnit(unit.getId(), normalizedUnitType(unit), unit.getName(), unit.getLogoUrl());
    }

    /** One page of manageable units for /sessions/scope; tenant-wide callers are paged in SQL. */
    SessionScopeUnits pageUnits(ScopeCaller caller, String query, int page, int size) {
        String needle = safe(query).toLowerCase(Locale.ROOT);
        if (caller.tenantWide()) {
            Page<EducationUnit> found = educationUnitRepository.findDirectory(
                    caller.tenantId(), needle.isEmpty() ? null : needle, null, PageRequest.of(page, size));
            return new SessionScopeUnits(toBootstrapUnits(found.getContent().stream().map(this::toManagedUnit).toList()),
                    page, size, found.getTotalElements(), found.hasNext());
        }
        // shortcut: scoped and platform-wide callers are filtered and paged in memory over the 500-unit cap; add a repository query to lift it.
        List<ManagedUnit> matching = resolveManageableUnits(caller).stream()
                .filter(unit -> needle.isEmpty() || safe(unit.name()).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int from = (int) Math.min((long) page * size, matching.size());
        int to = Math.min(from + size, matching.size());
        return new SessionScopeUnits(toBootstrapUnits(matching.subList(from, to)), page, size, matching.size(), to < matching.size());
    }

    /** Targeted check: a tenant-wide caller needs one lookup, never the full list. */
    Optional<ManagedUnit> findManageableUnit(ScopeCaller caller, String unitId) {
        if (caller.tenantWide()) {
            return educationUnitRepository.findByIdAndTenantId(unitId, caller.tenantId()).map(this::toManagedUnit);
        }
        return resolveManageableUnits(caller).stream().filter(unit -> unitId.equals(unit.id())).findFirst();
    }

    /** Classes of one manageable unit, or (plain teachers only) the teacher's own classes; admins must pick a unit first. */
    SessionScopeClasses scopeClasses(ScopeCaller caller, String unitId, int size) {
        List<ManagedUnit> units;
        if (!safe(unitId).isEmpty()) {
            units = List.of(findManageableUnit(caller, unitId.trim())
                    .orElseThrow(() -> new ForbiddenException("education unit is not manageable by this session")));
        } else if (caller.teacherId() != null) {
            units = resolveManageableUnits(caller);
        } else {
            return new SessionScopeClasses(List.of(), false);
        }
        List<SessionBootstrapClass> rows = classRows(caller.tenantId(), units, caller.teacherId(), size);
        boolean more = rows.size() > size;
        return new SessionScopeClasses(more ? rows.subList(0, size) : rows, more);
    }

    /** Same validation as {@link #resolveCurrentScope} without loading every class of the tenant. */
    SessionBootstrapScope resolveCurrentScopeTargeted(UserAccessContext requestedAccess, ScopeCaller caller) {
        String classId = safe(requestedAccess.currentClassId());
        String unitId = safe(requestedAccess.currentEducationUnitId());
        if (!classId.isEmpty()) {
            return classroomRepository.findByTenantIdAndId(caller.tenantId(), classId)
                    .filter(classroom -> classroom.getSchoolId() != null && findManageableUnit(caller, classroom.getSchoolId()).isPresent())
                    .filter(classroom -> caller.teacherId() == null || classroomRepository
                            .findTeacherClassOptions(caller.tenantId(), caller.teacherId()).stream()
                            .anyMatch(option -> classId.equals(option.id())))
                    .map(classroom -> new SessionBootstrapScope("class", classroom.getSchoolId(), classroom.getId()))
                    .orElseGet(() -> fallbackFor(caller));
        }
        if (!unitId.isEmpty()) {
            return findManageableUnit(caller, unitId)
                    .map(unit -> new SessionBootstrapScope(levelFromUnitType(unit.type()), unit.id(), null))
                    .orElseGet(() -> fallbackFor(caller));
        }
        return fallbackFor(caller);
    }

    private SessionBootstrapScope fallbackFor(ScopeCaller caller) {
        return fallbackScope(caller.tenantWide() ? tenantWideFallbackUnits(caller.tenantId()) : resolveManageableUnits(caller),
                caller.canAccessGlobalErg());
    }

    /**
     * The fallback is the first unit by type (system, centre, school) then name. Names alone order the first page, so a
     * tenant with more units than that page loads the full bootstrap window instead of risking a school ahead of the system unit.
     */
    private List<ManagedUnit> tenantWideFallbackUnits(String tenantId) {
        Page<EducationUnit> first = educationUnitRepository.findDirectory(tenantId, null, null, PageRequest.of(0, 50));
        return first.hasNext()
                ? sortedUnits(educationUnitRepository.findByTenantIdOrderByNameAsc(tenantId, PageRequest.of(0, MAX_BOOTSTRAP_UNITS)))
                : sortedUnits(first.getContent());
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

    /**
     * {@code teacherId} non-null means a plain teacher (no back-office workspace): only the classes they are homeroom
     * teacher of, have assigned work to, or teach in the published timetable; never every class of the school.
     */
    List<SessionBootstrapClass> resolveManageableClasses(String tenantId, List<ManagedUnit> managedUnits, String teacherId) {
        List<SessionBootstrapClass> rows = classRows(tenantId, managedUnits, teacherId, MAX_BOOTSTRAP_CLASSES);
        if (rows.size() <= MAX_BOOTSTRAP_CLASSES) {
            return rows;
        }
        log.warn("session bootstrap classes capped at {} for tenant {}; the caller manages more", MAX_BOOTSTRAP_CLASSES, tenantId);
        return rows.subList(0, MAX_BOOTSTRAP_CLASSES);
    }

    /** At most {@code limit + 1} rows: the extra row tells the caller the list was cut. */
    private List<SessionBootstrapClass> classRows(String tenantId, List<ManagedUnit> managedUnits, String teacherId, int limit) {
        List<String> schoolIds = managedUnits.stream()
                .map(ManagedUnit::id)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (schoolIds.isEmpty()) {
            return List.of();
        }
        if (teacherId != null) {
            return classroomRepository.findTeacherClassOptions(tenantId, teacherId).stream()
                    .filter(option -> schoolIds.contains(option.schoolId()))
                    .limit(limit + 1L)
                    .map(option -> new SessionBootstrapClass(option.id(), option.name(), option.schoolId(),
                            option.grade(), option.academicYear(), option.status()))
                    .toList();
        }

        List<Classroom> classes = classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(
                tenantId, schoolIds, PageRequest.of(0, limit + 1));
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

    private List<EducationUnit> scopedEducationUnits(ScopeCaller caller) {
        User user = caller.user();
        String tenantId = caller.tenantId();
        List<UserAccessScope> scopes = accessScopes(user).stream()
                .filter(AccessScopeAssembler::appliesToLms)
                .toList();
        Set<String> directIds = new LinkedHashSet<>();
        Set<String> educationUnitAdminRootIds = new LinkedHashSet<>();
        boolean userHoldsClassManage = holdsClassManage(user);

        for (UserAccessScope scope : scopes) {
            String educationUnitId = safe(scope.getEducationUnitId());
            if (educationUnitId.isBlank()) {
                continue;
            }
            directIds.add(educationUnitId);
            if (userHoldsClassManage) {
                educationUnitAdminRootIds.add(educationUnitId);
            }
        }

        if (user != null && user.getId() != null) {
            for (UserRoleAssignment assignment : caller.assignments()) {
                String scopeId = safe(assignment.getScopeId());
                String scopeType = safe(assignment.getScopeType()).toUpperCase(Locale.ROOT);
                if (scopeId.isBlank()) {
                    continue;
                }
                if (AccessScopeAssembler.isEducationUnitScopeType(scopeType)) {
                    directIds.add(scopeId);
                    if (userHoldsClassManage || holdsClassManage(assignment.getRole())) {
                        educationUnitAdminRootIds.add(scopeId);
                    }
                }
            }
        }

        // Teaching assignments are data: anyone assigned to teach a class gets its school, whatever their role names.
        if (user != null && user.getId() != null) {
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

    /** A direct role holding {@code lms.class.manage} makes every one of the user's legacy scopes a unit-admin root. */
    private boolean holdsClassManage(User user) {
        return user != null && user.getRoles() != null && user.getRoles().stream().anyMatch(this::holdsClassManage);
    }

    private boolean holdsClassManage(Role role) {
        return role != null && role.getPermissions() != null && AccessScopeAssembler.isEducationUnitAdmin(
                role.getPermissions().stream().filter(Objects::nonNull).map(Permission::getName).toList());
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
