package com.erg.ergspringboot.service.authorization;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AddPermissionOverrideRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.CreateRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.EffectivePermissionsResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.FeatureAccessResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.FeatureConfigResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionGroupResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionOverrideResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RoleResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.PermissionGroup;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final UserRepository userRepository;

    public AuthorizationService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserPermissionOverrideRepository overrideRepository,
            UserRepository userRepository
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.overrideRepository = overrideRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "authorizationRoles", key = "'all'")
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream().map(this::toRoleResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(String roleId) {
        Role role = roleRepository.findById(roleId)
                .or(() -> roleRepository.findByNameIgnoreCase(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        return toRoleResponse(role);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions", "authPrincipal", "sessionBootstrap"}, allEntries = true)
    public RoleResponse createRole(CreateRoleRequest request) {
        Role role = new Role();
        role.setId(IdGenerator.newId());
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setIsDefault(false);
        role.setPermissions(resolvePermissions(request.permissions()));
        return toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions", "authPrincipal", "sessionBootstrap"}, allEntries = true)
    public RoleResponse updateRole(String roleId, UpdateRoleRequest request) {
        Role role = roleRepository.findById(roleId)
                .or(() -> roleRepository.findByNameIgnoreCase(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        if (request.name() != null) role.setName(request.name().trim());
        if (request.description() != null) role.setDescription(request.description());
        if (request.permissions() != null) role.setPermissions(resolvePermissions(request.permissions()));
        return toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authPrincipal", "sessionBootstrap"}, allEntries = true)
    public void deleteRole(String roleId) {
        Role role = roleRepository.findById(roleId)
                .or(() -> roleRepository.findByNameIgnoreCase(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "authorizationPermissions", key = "'all'")
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream().map(this::toPermissionResponse).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "authorizationPermissions", key = "'groups'")
    public List<PermissionGroupResponse> listPermissionGroups() {
        Map<String, List<Permission>> byGroup = permissionRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(permission ->
                        permission.getGroupName() == null ? "general" : permission.getGroupName()));
        return byGroup.entrySet().stream()
                .map(entry -> new PermissionGroupResponse(
                        entry.getKey(),
                        entry.getKey(),
                        entry.getKey(),
                        0,
                        entry.getValue().stream().map(this::toPermissionResponse).toList()
                ))
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public RoleResponse assignRoles(String userId, List<String> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        List<Role> roles = resolveRoles(roleIds);
        if (roles.size() != new LinkedHashSet<>(roleIds).size()) {
            throw new BadRequestException("one or more roles were not found");
        }
        user.setRoles(new LinkedHashSet<>(roles));
        userRepository.save(user);
        return roles.stream().findFirst().map(this::toRoleResponse)
                .orElseThrow(() -> new BadRequestException("at least one role is required"));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public void removeRole(String userId, String roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        Role role = roleRepository.findById(roleId)
                .or(() -> roleRepository.findByNameIgnoreCase(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        Set<Role> roles = user.getRoles() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(user.getRoles());
        roles.removeIf(item -> item.getId().equals(role.getId()) || item.getName().equalsIgnoreCase(role.getName()));
        user.setRoles(roles);
        userRepository.save(user);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public long bulkAssignRole(BulkAssignRoleRequest request) {
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        List<User> users = userRepository.findAllById(request.userIds());
        users.forEach(user -> {
            Set<Role> roles = user.getRoles() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(user.getRoles());
            roles.add(role);
            user.setRoles(roles);
        });
        userRepository.saveAll(users);
        return users.size();
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public PermissionOverrideResponse addPermissionOverride(String userId, AddPermissionOverrideRequest request, String actorId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("user not found"));
        Permission permission = permissionRepository.findByNameInIgnoreCase(List.of(request.permission()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("permission not found"));
        UserPermissionOverride override = new UserPermissionOverride();
        override.setId(IdGenerator.newId());
        override.setUserId(userId);
        override.setPermission(permission.getName());
        override.setGrantType(normalizeGrantType(request.grantType()));
        override.setReason(request.reason());
        override.setExpiresAt(request.expiresAt());
        override.setCreatedBy(actorId);
        override.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toOverrideResponse(overrideRepository.save(override));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public void deletePermissionOverride(String userId, String overrideId) {
        UserPermissionOverride override = overrideRepository.findById(overrideId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("permission override not found"));
        overrideRepository.delete(override);
    }

    @Transactional(readOnly = true)
    public List<PermissionOverrideResponse> listPermissionOverrides(String userId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("user not found"));
        return overrideRepository.findByUserId(userId).stream().map(this::toOverrideResponse).toList();
    }

    @Transactional(readOnly = true)
    public EffectivePermissionsResponse effectivePermissions(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        List<String> rolePermissions = user.getRoles() == null ? List.of() : user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .distinct()
                .toList();
        List<UserPermissionOverride> overrides = overrideRepository.findByUserId(userId);
        List<String> grants = overrides.stream()
                .filter(item -> "GRANT".equalsIgnoreCase(item.getGrantType()))
                .map(UserPermissionOverride::getPermission)
                .toList();
        List<String> denies = overrides.stream()
                .filter(item -> "DENY".equalsIgnoreCase(item.getGrantType()))
                .map(UserPermissionOverride::getPermission)
                .toList();
        List<String> effective = java.util.stream.Stream.concat(rolePermissions.stream(), grants.stream())
                .filter(permission -> denies.stream().noneMatch(permission::equalsIgnoreCase))
                .distinct()
                .toList();
        return new EffectivePermissionsResponse(userId, rolePermissions, grants, denies, effective);
    }

    @Transactional(readOnly = true)
    public FeatureConfigResponse featureConfig(String userId) {
        EffectivePermissionsResponse permissions = effectivePermissions(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles().stream().map(Role::getName).toList();
        FeatureAccessResponse access = new FeatureAccessResponse(
                permissions.effectivePermissions(),
                permissions.effectivePermissions(),
                permissions.effectivePermissions()
        );
        Map<String, Boolean> features = permissions.effectivePermissions().stream()
                .collect(java.util.stream.Collectors.toMap(item -> item, item -> true, (a, b) -> a));
        return new FeatureConfigResponse(userId, permissions.effectivePermissions(), features, access, roles);
    }

    @Transactional(readOnly = true)
    public PreviewChangesResponse previewChanges(String userId, PreviewChangesRequest request) {
        EffectivePermissionsResponse current = effectivePermissions(userId);
        List<String> simulated = new java.util.ArrayList<>(current.effectivePermissions());
        Object grant = request.changes() == null ? null : request.changes().get("grant");
        if (grant instanceof String permission && !permission.isBlank()) {
            simulated.add(permission);
        }
        Object deny = request.changes() == null ? null : request.changes().get("deny");
        if (deny instanceof String permission && !permission.isBlank()) {
            simulated.removeIf(item -> item.equalsIgnoreCase(permission));
        }
        return new PreviewChangesResponse(
                current.effectivePermissions(),
                request.changes() == null ? Map.of() : request.changes(),
                simulated.stream().distinct().toList()
        );
    }

    private LinkedHashSet<Permission> resolvePermissions(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Permission> permissions = permissionRepository.findByNameInIgnoreCase(names);
        if (permissions.size() != new LinkedHashSet<>(names).size()) {
            throw new BadRequestException("one or more permissions were not found");
        }
        return new LinkedHashSet<>(permissions);
    }

    private List<Role> resolveRoles(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Role> byId = roleRepository.findAllById(roleIds);
        Set<String> found = byId.stream()
                .flatMap(role -> java.util.stream.Stream.of(role.getId().toLowerCase(), role.getName().toLowerCase()))
                .collect(java.util.stream.Collectors.toSet());
        List<String> missing = roleIds.stream()
                .filter(roleId -> !found.contains(roleId.toLowerCase()))
                .toList();
        List<Role> byName = missing.isEmpty() ? List.of() : roleRepository.findByNameInIgnoreCase(missing);
        return java.util.stream.Stream.concat(byId.stream(), byName.stream())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(Role::getId, role -> role, (left, right) -> left, java.util.LinkedHashMap::new),
                        map -> new java.util.ArrayList<>(map.values())
                ));
    }

    private RoleResponse toRoleResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions() == null ? List.of() : role.getPermissions().stream().map(Permission::getName).toList(),
                Boolean.TRUE.equals(role.getIsDefault()),
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getGroupName(),
                permission.getLabel(),
                permission.getDescription()
        );
    }

    private PermissionOverrideResponse toOverrideResponse(UserPermissionOverride override) {
        return new PermissionOverrideResponse(
                override.getId(),
                override.getUserId(),
                override.getPermission(),
                override.getGrantType(),
                override.getReason(),
                override.getExpiresAt(),
                override.getCreatedBy(),
                override.getCreatedAt()
        );
    }

    private String normalizeGrantType(String grantType) {
        String normalized = grantType.trim().toUpperCase();
        if (!List.of("GRANT", "DENY").contains(normalized)) {
            throw new BadRequestException("invalid permission grant type");
        }
        return normalized;
    }
}
