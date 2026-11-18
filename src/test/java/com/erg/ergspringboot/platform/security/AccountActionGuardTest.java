package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.AccountActionGuard.AccountAction;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.RoleRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountActionGuardTest {

    private static final List<String> PDT_PERMISSIONS = List.of(
            "users.manage", "users.read", "lms.class.manage", "lms.class.read", "rbac.assignment.read");

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
    private final AccountActionGuard guard = new AccountActionGuard(users, roles, assignments);

    private final Role superAdminRole = role("role_super_admin", "*");
    private final Role directorRole = role("role_director", "lms.class.read", "audit.read");
    private final Role operationsRole = role("role_operations", "lms.class.read");
    private final Role admissionsRole = role("role_admissions", "crm.workspace.access", "crm.lead.manage");
    private final Role teacherRole = role("role_lms_teacher", "lms.class.read");
    private final Role pdtRole = role("role_pdt_operator", PDT_PERMISSIONS.toArray(String[]::new));

    private final ErgPrincipal pdt = principal("pdt-1", List.of("pdt_operator"), PDT_PERMISSIONS);
    private final ErgPrincipal superAdmin = principal("sa-1", List.of("system.super_admin"), List.of("*"));

    @Test
    void studentRoleCannotBeCombinedWithAnyStaffRole() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AccountActionGuard.assertRoleCombination(
                java.util.List.of("role_lms_teacher", "role_lms_student")))
                .isInstanceOf(com.erg.ergspringboot.exception.BadRequestException.class);
        AccountActionGuard.assertRoleCombination(java.util.List.of("role_lms_student"));
    }

    @Test
    void pdtCannotResetSuperAdminPassword() {
        target("admin-2", superAdminRole);

        assertThatThrownBy(() -> guard.assertCanActOn(pdt, "admin-2", AccountAction.RESET_PASSWORD))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void pdtCannotAssignCrmRole() {
        target("teacher-1", teacherRole);
        when(roles.findAllById(any())).thenReturn(List.of(teacherRole, admissionsRole));

        assertThatThrownBy(() -> guard.assertCanAssign(pdt, "teacher-1",
                Set.of("role_lms_teacher", "role_admissions"), null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void pdtCanManageTeacher() {
        target("teacher-1", teacherRole);
        when(roles.findAllById(any())).thenReturn(List.of(teacherRole));

        assertThatCode(() -> guard.assertCanActOn(pdt, "teacher-1", AccountAction.LOCK)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertCanAssign(pdt, "teacher-1", Set.of("role_lms_teacher"), null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void pdtCannotTouchDirectorOrAdmissions() {
        target("director-1", directorRole);
        target("admissions-1", admissionsRole);

        assertThatThrownBy(() -> guard.assertCanActOn(pdt, "director-1", AccountAction.LOCK))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.assertCanActOn(pdt, "admissions-1", AccountAction.LOCK))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cannotActOnSelf() {
        target("pdt-1", pdtRole);

        assertThatThrownBy(() -> guard.assertCanActOn(pdt, "pdt-1", AccountAction.DELETE))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.assertCanAssign(pdt, "pdt-1", Set.of("role_pdt_operator"), null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cannotRemoveLastSuperAdmin() {
        User onlyAdmin = target("admin-2", superAdminRole);
        superAdminRole.setUsers(new LinkedHashSet<>(Set.of(onlyAdmin)));
        when(roles.findById("role_super_admin")).thenReturn(Optional.of(superAdminRole));
        when(roles.findAllById(any())).thenReturn(List.of(teacherRole));

        assertThatThrownBy(() -> guard.assertCanActOn(superAdmin, "admin-2", AccountAction.DELETE))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> guard.assertCanActOn(superAdmin, "admin-2", AccountAction.LOCK))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> guard.assertCanAssign(superAdmin, "admin-2", Set.of("role_lms_teacher"), null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void canRemoveSuperAdminWhenAnotherActiveOneExists() {
        User first = target("admin-2", superAdminRole);
        User second = user("admin-3");
        second.setStatus("ACTIVE");
        superAdminRole.setUsers(new LinkedHashSet<>(List.of(first, second)));
        when(roles.findById("role_super_admin")).thenReturn(Optional.of(superAdminRole));

        assertThatCode(() -> guard.assertCanActOn(superAdmin, "admin-2", AccountAction.DELETE))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAddingRoleToDirector() {
        target("director-1", directorRole);
        when(roles.findAllById(any())).thenReturn(List.of(directorRole, teacherRole));

        assertThatThrownBy(() -> guard.assertCanAssign(superAdmin, "director-1",
                Set.of("role_director", "role_lms_teacher"), null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void bulkOver100Rejected() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) ids.add("u" + i);

        assertThatThrownBy(() -> guard.assertCanActOnAll(superAdmin, ids, AccountAction.DELETE))
                .isInstanceOf(BadRequestException.class);
        verify(users, never()).findByIdAndTenantId(anyString(), anyString());
    }

    @Test
    void bulkRejectsWholeBatchWhenOneTargetIsAdmin() {
        target("teacher-1", teacherRole);
        target("admin-2", superAdminRole);

        assertThatThrownBy(() -> guard.assertCanActOnAll(pdt, List.of("teacher-1", "admin-2"), AccountAction.DELETE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assignableRolesForPdtExcludesAdminDirectorAdmissions() {
        when(roles.findAllWithPermissionsAndUsers()).thenReturn(List.of(
                superAdminRole, directorRole, operationsRole, admissionsRole, teacherRole, pdtRole));

        assertThat(guard.assignableRoles(pdt)).extracting(AccountActionGuard.AssignableRole::id)
                .containsExactly("role_operations", "role_lms_teacher", "role_pdt_operator");
        assertThat(guard.assignableRoles(superAdmin)).hasSize(6);
    }

    private User target(String id, Role... heldRoles) {
        User user = user(id);
        user.setRoles(new LinkedHashSet<>(List.of(heldRoles)));
        when(users.findByIdAndTenantId(id, "erg")).thenReturn(Optional.of(user));
        return user;
    }

    private User user(String id) {
        User user = new User();
        user.setId(id);
        user.setTenantId("erg");
        user.setStatus("ACTIVE");
        return user;
    }

    private Role role(String id, String... permissions) {
        Role role = new Role();
        role.setId(id);
        role.setName(id);
        LinkedHashSet<Permission> values = new LinkedHashSet<>();
        for (String name : permissions) {
            Permission permission = new Permission();
            permission.setName(name);
            values.add(permission);
        }
        role.setPermissions(values);
        return role;
    }

    private ErgPrincipal principal(String id, List<String> roleNames, List<String> permissions) {
        return new ErgPrincipal(id, "session-1", "erg", roleNames, permissions, List.of(), List.of(),
                "erg", "admin", Map.of());
    }
}
