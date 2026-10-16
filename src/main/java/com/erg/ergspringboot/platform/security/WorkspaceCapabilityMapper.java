package com.erg.ergspringboot.platform.security;

import java.util.Locale;
import java.util.Optional;
import java.util.List;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class WorkspaceCapabilityMapper {
    private WorkspaceCapabilityMapper() {}

    public static Optional<WorkspaceCapability> fromLegacyPortal(String portal) {
        if (portal == null) return Optional.empty();
        return switch (portal.trim().toLowerCase(Locale.ROOT)) {
            case "admin", "lcms" -> Optional.of(WorkspaceCapability.ADMIN);
            case "lms" -> Optional.of(WorkspaceCapability.TEACHER);
            case "elearning" -> Optional.of(WorkspaceCapability.STUDENT);
            case "crm" -> Optional.of(WorkspaceCapability.CRM);
            default -> Optional.empty();
        };
    }

    public static List<String> canonicalWorkspaces(List<String> portals) {
        return canonicalWorkspaces(portals, List.of(), List.of());
    }

    /**
     * Projects authenticated, server-derived claims into the public workspace
     * vocabulary. Unknown values are ignored. A wildcard is deliberately
     * explicit and means every workspace; no other client-controlled token
     * grants access.
     */
    public static List<String> canonicalWorkspaces(
            List<String> portals,
            List<String> roles,
            List<String> permissions
    ) {
        Set<WorkspaceCapability> capabilities = EnumSet.noneOf(WorkspaceCapability.class);
        if (hasWildcard(portals) || hasWildcard(roles) || hasWildcard(permissions)) {
            capabilities.addAll(EnumSet.allOf(WorkspaceCapability.class));
        } else {
            addTokens(capabilities, portals);
            addTokens(capabilities, roles);
            addTokens(capabilities, permissions);
        }
        return capabilities.stream().map(Enum::name).sorted().toList();
    }

    /**
     * Derives workspace capabilities exclusively from verified server-side role
     * and permission grants. Legacy portal values are deliberately not accepted
     * here because they are a representation hint, not authority.
     */
    public static List<String> canonicalWorkspacesFromVerifiedGrants(
            List<String> roles,
            List<String> permissions,
            List<String> deniedPermissions) {
        Set<WorkspaceCapability> capabilities = EnumSet.noneOf(WorkspaceCapability.class);
        boolean systemRole = containsNormalized(roles, "system.super_admin", "erg_super_admin", "global_admin");
        boolean wildcardGrant = containsNormalized(permissions, "*");
        boolean wildcardDeny = containsNormalized(deniedPermissions, "*");
        if (systemRole && wildcardGrant && !wildcardDeny) {
            capabilities.addAll(EnumSet.allOf(WorkspaceCapability.class));
        } else {
            addVerifiedTokens(capabilities, roles);
            addVerifiedTokens(capabilities, permissions);
        }
        removeDenied(capabilities, deniedPermissions);
        return capabilities.stream().map(Enum::name).sorted().toList();
    }

    /**
     * Keeps the old portal projection available while callers migrate to
     * canonical workspace names. This is a representation adapter, not an
     * authorization check.
     */
    public static List<String> legacyPortalsFor(
            List<String> portals,
            List<String> roles,
            List<String> permissions
    ) {
        if (hasWildcard(portals) || hasWildcard(roles) || hasWildcard(permissions)) {
            return List.of("*");
        }
        Set<String> legacy = new LinkedHashSet<>();
        for (String workspace : canonicalWorkspaces(portals, roles, permissions)) {
            switch (workspace) {
                case "ADMIN" -> legacy.add("lcms");
                case "TEACHER" -> legacy.add("lms");
                case "STUDENT" -> legacy.add("elearning");
                case "CRM" -> legacy.add("crm");
                default -> { }
            }
        }
        return legacy.stream().sorted().toList();
    }

    private static void addTokens(Set<WorkspaceCapability> capabilities, List<String> values) {
        if (values == null) return;
        values.stream()
                .map(WorkspaceCapabilityMapper::fromCapabilityToken)
                .flatMap(Optional::stream)
                .forEach(capabilities::add);
    }

    private static void addVerifiedTokens(Set<WorkspaceCapability> capabilities, List<String> values) {
        if (values == null) return;
        values.stream()
                .map(WorkspaceCapabilityMapper::fromVerifiedCapabilityToken)
                .flatMap(Optional::stream)
                .forEach(capabilities::add);
    }

    private static void removeDenied(Set<WorkspaceCapability> capabilities, List<String> deniedPermissions) {
        if (deniedPermissions == null) return;
        deniedPermissions.stream()
                .map(WorkspaceCapabilityMapper::fromVerifiedCapabilityToken)
                .flatMap(Optional::stream)
                .forEach(capabilities::remove);
        if (containsNormalized(deniedPermissions, "workspace.*", "*")) {
            capabilities.clear();
        }
    }

    private static boolean containsNormalized(List<String> values, String... expected) {
        if (values == null) return false;
        Set<String> wanted = Set.of(expected);
        return values.stream().filter(value -> value != null)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(wanted::contains);
    }

    private static boolean hasWildcard(List<String> values) {
        return values != null && values.stream().anyMatch(value -> value != null && "*".equals(value.trim()));
    }

    private static Optional<WorkspaceCapability> fromCapabilityToken(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "admin", "administrator", "super_admin", "erg_admin", "erg_super_admin", "global_admin", "lms_admin" -> Optional.of(WorkspaceCapability.ADMIN);
            case "lcms" -> Optional.of(WorkspaceCapability.ADMIN);
            case "lms", "teacher", "instructor", "teacher_role" -> Optional.of(WorkspaceCapability.TEACHER);
            case "student", "learner", "elearning" -> Optional.of(WorkspaceCapability.STUDENT);
            case "crm", "crm_manager" -> Optional.of(WorkspaceCapability.CRM);
            default -> {
                if (normalized.startsWith("admin.") || normalized.startsWith("lcms.")) {
                    yield Optional.of(WorkspaceCapability.ADMIN);
                }
                if (normalized.startsWith("crm.")) {
                    yield Optional.of(WorkspaceCapability.CRM);
                }
                if (normalized.startsWith("elearning.") || normalized.startsWith("student.")) {
                    yield Optional.of(WorkspaceCapability.STUDENT);
                }
                if (normalized.startsWith("lms.") || normalized.startsWith("teacher.") || normalized.startsWith("instructor.")) {
                    yield Optional.of(WorkspaceCapability.TEACHER);
                }
                yield Optional.empty();
            }
        };
    }

    private static Optional<WorkspaceCapability> fromVerifiedCapabilityToken(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "admin", "administrator", "super_admin", "erg_admin", "erg_super_admin",
                    "global_admin", "lms_admin", "rbac.*", "roles.*", "content.*",
                    "curriculum.*", "media.*", "workspace.admin", "admin.*" -> Optional.of(WorkspaceCapability.ADMIN);
            case "lms", "teacher", "instructor", "teacher_role", "workspace.teacher" -> Optional.of(WorkspaceCapability.TEACHER);
            case "student", "learner", "elearning", "workspace.student" -> Optional.of(WorkspaceCapability.STUDENT);
            case "crm", "crm_manager", "workspace.crm" -> Optional.of(WorkspaceCapability.CRM);
            default -> {
                if (normalized.startsWith("admin.") || normalized.startsWith("lcms.")
                        || normalized.startsWith("rbac.") || normalized.startsWith("content.")
                        || normalized.startsWith("curriculum.") || normalized.startsWith("media.")) {
                    yield Optional.of(WorkspaceCapability.ADMIN);
                }
                if (normalized.startsWith("crm.")) yield Optional.of(WorkspaceCapability.CRM);
                if (normalized.startsWith("elearning.") || normalized.startsWith("student.")) {
                    yield Optional.of(WorkspaceCapability.STUDENT);
                }
                if (normalized.startsWith("lms.") || normalized.startsWith("teacher.")
                        || normalized.startsWith("instructor.")) {
                    yield Optional.of(WorkspaceCapability.TEACHER);
                }
                yield Optional.empty();
            }
        };
    }
}
