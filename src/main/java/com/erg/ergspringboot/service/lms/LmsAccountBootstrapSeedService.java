package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.repository.organization.UserAccessScopeRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsAccountBootstrapSeedService {

    private static final String TENANT_ID = "erg";
    private static final String ACTIVE = "ACTIVE";
    private static final String ADMIN_EMAIL = "admin@erg.edu.vn";
    private static final String SYSTEM_ROLE = "system.super_admin";
    private static final String SYSTEM_UNIT_ID = "ctr_system";
    private static final String STUDIO_UNIT_ID = "ctr_hoclieu";
    private static final String BINH_AN_SCHOOL_ID = "sch_binh_an";
    private static final String CAT_LAI_SCHOOL_ID = "sch_cat_lai";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final EducationUnitRepository educationUnitRepository;
    private final UserAccessScopeRepository userAccessScopeRepository;

    public LmsAccountBootstrapSeedService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            EducationUnitRepository educationUnitRepository,
            UserAccessScopeRepository userAccessScopeRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.educationUnitRepository = educationUnitRepository;
        this.userAccessScopeRepository = userAccessScopeRepository;
    }

    @Transactional("postgresTransactionManager")
    public void seedAccountHierarchy() {
        Permission wildcardPermission = upsertPermission("perm_system_all_001", "*", "system", "Full system access");
        Permission profileSelfPermission = upsertPermission("perm_profile_self_001", "profile.self", "profile", "Access own session/profile");
        Permission lmsScopeReadPermission = upsertPermission("perm_lms_scope_read", "lms.scope.read", "lms", "Read LMS scope");
        Permission lmsUnitReadPermission = upsertPermission("perm_lms_unit_read_1", "lms.unit.read", "lms", "Read education units");
        Permission lmsClassReadPermission = upsertPermission("perm_lms_class_read", "lms.class.read", "lms", "Read classes");

        Role systemRole = roleRepository.findByNameIgnoreCase(SYSTEM_ROLE)
                .orElseGet(() -> {
                    Role created = new Role();
                    created.setId("role_system_super_admin");
                    created.setName(SYSTEM_ROLE);
                    return created;
                });
        systemRole.setDescription("System super admin for ERG bootstrap data");
        systemRole.setIsDefault(Boolean.FALSE);
        systemRole.setPermissions(new LinkedHashSet<>(List.of(
                wildcardPermission,
                profileSelfPermission,
                lmsScopeReadPermission,
                lmsUnitReadPermission,
                lmsClassReadPermission
        )));
        roleRepository.save(systemRole);

        upsertEducationUnit(SYSTEM_UNIT_ID, "He thong ERG", "erg-system", "system", null, "System unit for all schools");
        upsertEducationUnit(STUDIO_UNIT_ID, "Hoc Lieu Studio", "hoc-lieu-studio", "center", SYSTEM_UNIT_ID, "Learning content and school operations unit");
        upsertEducationUnit(BINH_AN_SCHOOL_ID, "THCS Binh An", "thcs-binh-an", "school", STUDIO_UNIT_ID, "THCS Binh An");
        upsertEducationUnit(CAT_LAI_SCHOOL_ID, "THCS Cat Lai", "thcs-cat-lai", "school", STUDIO_UNIT_ID, "THCS Cat Lai");

        setEducationUnitLogo(SYSTEM_UNIT_ID, "/assets/units/erg-system.svg");
        setEducationUnitLogo(STUDIO_UNIT_ID, "/assets/units/hoc-lieu-studio.svg");
        setEducationUnitLogo(BINH_AN_SCHOOL_ID, "/assets/units/thcs-binh-an.svg");
        setEducationUnitLogo(CAT_LAI_SCHOOL_ID, "/assets/units/thcs-cat-lai.svg");

        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(user -> {
            user.setTenantId(TENANT_ID);
            if (user.getFullName() == null || user.getFullName().isBlank()) {
                user.setFullName("ERG Super Admin");
            }
            Set<Role> roles = user.getRoles() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(user.getRoles());
            roles.add(systemRole);
            user.setRoles(roles);
            userRepository.save(user);
            upsertAccessScope("scope_admin_system_001", user, SYSTEM_UNIT_ID, "lms,hoclieu,cms", SYSTEM_ROLE);
        });
    }

    private Permission upsertPermission(String id, String name, String groupName, String label) {
        Permission permission = permissionRepository.findByNameInIgnoreCase(List.of(name)).stream()
                .findFirst()
                .orElseGet(() -> {
                    Permission created = new Permission();
                    created.setId(id);
                    created.setName(name);
                    return created;
                });
        permission.setGroupName(groupName);
        permission.setLabel(label);
        permission.setDescription(label);
        return permissionRepository.save(permission);
    }

    private EducationUnit upsertEducationUnit(String id, String name, String slug, String type, String parentId, String description) {
        EducationUnit educationUnit = educationUnitRepository.findById(id).orElseGet(() -> {
            EducationUnit created = new EducationUnit();
            created.setId(id);
            return created;
        });
        educationUnit.setName(name);
        educationUnit.setSlug(slug);
        educationUnit.setType(type);
        educationUnit.setParentId(parentId);
        educationUnit.setDescription(description);
        educationUnit.setStatus(ACTIVE);
        return educationUnitRepository.save(educationUnit);
    }

    private void setEducationUnitLogo(String id, String logoUrl) {
        educationUnitRepository.findById(id).ifPresent(educationUnit -> {
            educationUnit.setLogoUrl(logoUrl);
            educationUnitRepository.save(educationUnit);
        });
    }

    private void upsertAccessScope(String id, User user, String educationUnitId, String modules, String role) {
        UserAccessScope scope = userAccessScopeRepository.findById(id).orElseGet(() -> {
            UserAccessScope created = new UserAccessScope();
            created.setId(id);
            return created;
        });
        scope.setUserId(user.getId());
        scope.setEducationUnitId(educationUnitId);
        scope.setModules(modules);
        scope.setRole(role);
        userAccessScopeRepository.save(scope);
    }
}
