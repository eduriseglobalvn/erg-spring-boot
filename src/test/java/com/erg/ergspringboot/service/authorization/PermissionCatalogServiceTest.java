package com.erg.ergspringboot.service.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.PermissionBinding;
import com.erg.ergspringboot.model.authorization.PermissionGroup;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.PermissionGroupRepository;
import com.erg.ergspringboot.repository.authorization.PermissionBindingRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class PermissionCatalogServiceTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final PermissionGroupRepository permissionGroupRepository = mock(PermissionGroupRepository.class);
    private final PermissionBindingRepository permissionBindingRepository = mock(PermissionBindingRepository.class);
    private final UserRoleAssignmentRepository userRoleAssignmentRepository = mock(UserRoleAssignmentRepository.class);
    private final UserPermissionOverrideRepository overrideRepository = mock(UserPermissionOverrideRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthorizationService service = new AuthorizationService(
            roleRepository, permissionRepository, overrideRepository, userRepository
    );

    {
        service.setPermissionGroupRepository(permissionGroupRepository);
        service.setPermissionBindingRepository(permissionBindingRepository);
        service.setUserRoleAssignmentRepository(userRoleAssignmentRepository);
    }

    @Test
    void permissionGroupsMustUsePersistedDatabaseIdentityInsteadOfSyntheticGroupName() {
        Permission permission = permission();
        PermissionGroup dbGroup = new PermissionGroup();
        dbGroup.setId("pg_lms");
        dbGroup.setName("lms");
        dbGroup.setLabel("LMS");
        dbGroup.setOrder(20);
        when(permissionGroupRepository.findAllByOrderByOrderAscNameAsc()).thenReturn(List.of(dbGroup));
        when(permissionRepository.findByGroupNameInOrderByGroupNameAscNameAsc(List.of("lms")))
                .thenReturn(List.of(permission));

        assertThat(service.listPermissionGroups())
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.id()).isEqualTo("pg_lms");
                    assertThat(group.label()).isEqualTo("LMS");
                    assertThat(group.order()).isEqualTo(20);
                });
        verify(permissionRepository).findByGroupNameInOrderByGroupNameAscNameAsc(List.of("lms"));
        verify(permissionRepository, never()).findByGroupNameOrderByNameAsc("lms");
    }

    @Test
    void permissionCatalogExposesDatabaseMetadataUsageAndBindings() {
        Permission permission = permission();
        Role role = new Role();
        role.setId("role-teacher");
        role.setPermissions(new LinkedHashSet<>(List.of(permission)));
        User teacher = new User();
        teacher.setId("user-teacher");
        role.setUsers(new LinkedHashSet<>(List.of(teacher)));

        PermissionBinding binding = new PermissionBinding();
        binding.setId("binding-rest");
        binding.setPermissionId(permission.getId());
        binding.setSurfaceType("REST");
        binding.setSurfaceIdentifier("POST /api/v1/lms/assignments");
        binding.setDescription("Create an assignment");
        binding.setEnforced(true);
        when(roleRepository.findAllWithPermissionsAndUsersByPermissionIds(List.of(permission.getId())))
                .thenReturn(List.of(role));
        when(permissionBindingRepository.findByPermissionIdInOrderByPermissionIdAscSurfaceTypeAscSurfaceIdentifierAsc(
                List.of(permission.getId()))).thenReturn(List.of(binding));
        when(permissionRepository.findTop500ByOrderByNameAsc()).thenReturn(List.of(permission));

        Map<String, Object> response = new ObjectMapper().findAndRegisterModules().convertValue(
                service.listPermissions().getFirst(),
                new TypeReference<>() { }
        );

        assertThat(response).containsEntry("resource", "assignment")
                .containsEntry("action", "create")
                .containsEntry("portal", "lms")
                .containsEntry("supportedScope", "school,class")
                .containsEntry("status", "ACTIVE")
                .containsEntry("isSystem", false)
                .containsEntry("used", true)
                .containsEntry("roleCount", 1)
                .containsEntry("userCount", 1);
        assertThat((List<?>) response.get("bindings")).singleElement();
    }

    @Test
    void permissionCatalogHasPagedServerSearchContract() {
        assertThat(Arrays.stream(AuthorizationService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .contains("searchPermissions");
    }

    @Test
    void rolesLoadPermissionsUsersAndScopedAssignmentsInBatches() {
        Role role = new Role();
        role.setId("role-teacher");
        role.setName("teacher");
        role.setPermissions(new LinkedHashSet<>());
        role.setUsers(new LinkedHashSet<>());
        when(roleRepository.findAllWithPermissionsAndUsers()).thenReturn(List.of(role));
        when(userRoleAssignmentRepository.findActiveForRoleIds(
                org.mockito.ArgumentMatchers.eq(List.of("role-teacher")), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        assertThat(service.listRoles()).singleElement().extracting(response -> response.id())
                .isEqualTo("role-teacher");

        verify(roleRepository).findAllWithPermissionsAndUsers();
        verify(userRoleAssignmentRepository).findActiveForRoleIds(
                org.mockito.ArgumentMatchers.eq(List.of("role-teacher")), org.mockito.ArgumentMatchers.any());
        verify(roleRepository, never()).findAll();
        verify(userRoleAssignmentRepository, never()).findActiveForRole(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void permissionCatalogPassesUsageStateToDatabaseFilterWithoutDroppingExistingFilters() {
        when(permissionRepository.searchCatalog(
                org.mockito.ArgumentMatchers.eq("assignment"),
                org.mockito.ArgumentMatchers.eq("lms"),
                org.mockito.ArgumentMatchers.eq("teacher"),
                org.mockito.ArgumentMatchers.eq("ACTIVE"),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(Page.empty());

        service.searchPermissions(0, 25, " assignment ", " lms ", " teacher ", " ACTIVE ", true);

        verify(permissionRepository).searchCatalog(
                org.mockito.ArgumentMatchers.eq("assignment"),
                org.mockito.ArgumentMatchers.eq("lms"),
                org.mockito.ArgumentMatchers.eq("teacher"),
                org.mockito.ArgumentMatchers.eq("ACTIVE"),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    private Permission permission() {
        Permission permission = new Permission();
        permission.setId("perm_lms_assignment_create");
        permission.setName("lms.assignment.create");
        permission.setGroupName("lms");
        permission.setLabel("Tạo bài tập");
        permission.setDescription("Cho phép tạo bài tập");
        permission.setResource("assignment");
        permission.setAction("create");
        permission.setPortal("lms");
        permission.setSupportedScope("school,class");
        permission.setIsSystem(false);
        permission.setStatus("ACTIVE");
        permission.setDeprecatedAt(OffsetDateTime.parse("2099-01-01T00:00:00Z"));
        return permission;
    }
}
