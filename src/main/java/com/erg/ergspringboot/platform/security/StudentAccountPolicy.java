package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The one place that decides whether an account is a student account (STU-01): its verified grants
 * project to the STUDENT workspace and nothing else. Student accounts are never held in the
 * first-login lifecycle (onboarding, forced password change, recovery email). A teacher who can also
 * open the student portal is not a student account.
 */
public final class StudentAccountPolicy {
    private static final List<String> STUDENT_ONLY = List.of(WorkspaceCapability.STUDENT.name());

    private StudentAccountPolicy() {
    }

    public static boolean isStudentOnly(List<String> roles, List<String> permissions, List<String> deniedPermissions) {
        return STUDENT_ONLY.equals(
                WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(roles, permissions, deniedPermissions));
    }

    /** Resolves roles from both the legacy {@code user_roles} set and the active scoped assignments. */
    public static boolean isStudentOnly(User user, Collection<UserRoleAssignment> activeAssignments, OffsetDateTime now) {
        EffectivePermissionResolver.Result resolved = EffectivePermissionResolver.resolve(user, activeAssignments, now);
        Stream<Role> legacy = user.getRoles() == null ? Stream.empty() : user.getRoles().stream();
        List<String> roles = Stream.concat(legacy, activeAssignments.stream().map(UserRoleAssignment::getRole))
                .filter(Objects::nonNull).map(Role::getName).filter(Objects::nonNull).toList();
        return isStudentOnly(roles, resolved.granted(), resolved.denied());
    }
}
