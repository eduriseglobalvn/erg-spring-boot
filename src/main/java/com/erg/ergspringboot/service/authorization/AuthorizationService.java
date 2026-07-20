package com.erg.ergspringboot.service.authorization;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AddPermissionOverrideRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.CreateRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.EffectivePermissionsResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.FeatureAccessResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.FeatureConfigResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionGroupResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionBindingResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionOverrideResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionPageResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RoleResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.PermissionBinding;
import com.erg.ergspringboot.model.authorization.PermissionGroup;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.ProtectedRolePolicy;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.PermissionBindingRepository;
import com.erg.ergspringboot.repository.authorization.PermissionGroupRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService.UserRef;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class AuthorizationService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final UserRepository userRepository;
    private final ProtectedRolePolicy protectedRolePolicy = new ProtectedRolePolicy();
    private PermissionGroupRepository permissionGroupRepository;
    private PermissionBindingRepository permissionBindingRepository;
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    private AuthorizationMutationAuditService mutationAuditService;

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

    @Autowired(required = false)
    void setPermissionGroupRepository(PermissionGroupRepository permissionGroupRepository) {
        this.permissionGroupRepository = permissionGroupRepository;
    }

    @Autowired(required = false)
    void setPermissionBindingRepository(PermissionBindingRepository permissionBindingRepository) {
        this.permissionBindingRepository = permissionBindingRepository;
    }

    @Autowired(required = false)
    void setUserRoleAssignmentRepository(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Autowired(required = false)
    void setMutationAuditService(AuthorizationMutationAuditService mutationAuditService) {
        this.mutationAuditService = mutationAuditService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "authorizationRoles", key = "'all'")
    public List<RoleResponse> listRoles() {
        List<Role> roles = roleRepository.findAllWithPermissionsAndUsers();
        Map<String, List<com.erg.ergspringboot.model.authorization.UserRoleAssignment>> assignmentsByRole =
                activeAssignmentsByRole(roles);
        return roles.stream()
                .filter(role -> !"system.god_mode".equalsIgnoreCase(role.getName()))
                .map(role -> toRoleResponse(role, assignmentsByRole.getOrDefault(role.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(String roleId) {
        Role role = roleRepository.findById(roleId)
                .or(() -> roleRepository.findByNameIgnoreCase(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        if ("system.god_mode".equalsIgnoreCase(role.getName())) {
            throw new ResourceNotFoundException("role not found");
        }
        return toRoleResponse(role);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions"}, allEntries = true)
    public RoleResponse createRole(CreateRoleRequest request) {
        ErgPrincipal actor = SecurityUtils.requirePrincipal();
        return createRole(request, actor, actorContext(actor));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions"}, allEntries = true)
    public RoleResponse createRole(CreateRoleRequest request, ErgPrincipal actor, AuditContext context) {
        protectedRolePolicy.assertRoleDefinitionAllowed(request.name());
        AuditContext requiredContext = requiredContext(actor, context);
        Role role = new Role();
        role.setId(IdGenerator.newId());
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setIsDefault(false);
        role.setUpdatedBy(requiredContext.actorUserId());
        role.setPermissions(resolvePermissions(request.permissions()));
        role = roleRepository.save(role);
        mutationAudit().recordRoleMutation(
                actorTenant(actor), role.getId(), "ROLE_CREATED", requiredContext,
                Map.of("roleName", role.getName()));
        return toRoleResponse(role);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions"}, allEntries = true)
    public RoleResponse updateRole(String roleId, UpdateRoleRequest request) {
        return updateRole(roleId, request, SecurityUtils.principal());
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions"}, allEntries = true)
    public RoleResponse updateRole(String roleId, UpdateRoleRequest request, ErgPrincipal actor) {
        return updateRole(roleId, request, actor, actorContext(actor));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions"}, allEntries = true)
    public RoleResponse updateRole(
            String roleId, UpdateRoleRequest request, ErgPrincipal actor, AuditContext context
    ) {
        Role role = roleRepository.findById(roleId)
                .or(() -> roleRepository.findByNameIgnoreCase(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        protectedRolePolicy.assertCanManageDefinition(actor, role);
        if (request.name() != null) {
            protectedRolePolicy.assertRoleDefinitionAllowed(request.name());
            role.setName(request.name().trim());
        }
        if (request.description() != null) role.setDescription(request.description());
        if (request.permissions() != null) role.setPermissions(resolvePermissions(request.permissions()));
        List<UserRef> affectedUsers = affectedUsers(role);
        AuditContext requiredContext = requiredContext(actor, context);
        role.setUpdatedBy(requiredContext.actorUserId());
        role = roleRepository.save(role);
        mutationAudit().recordRoleMutation(
                actorTenant(actor), role.getId(), "ROLE_UPDATED", requiredContext,
                Map.of("roleName", role.getName()));
        mutationAudit().authorizationChanged(affectedUsers, role.getId(), "ROLE_UPDATED", requiredContext);
        return toRoleResponse(role);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions"}, allEntries = true)
    public void deleteRole(String roleId) {
        deleteRole(roleId, SecurityUtils.principal());
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions"}, allEntries = true)
    public void deleteRole(String roleId, ErgPrincipal actor) {
        deleteRole(roleId, actor, actorContext(actor));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authorizationRoles", "authorizationPermissions"}, allEntries = true)
    public void deleteRole(String roleId, ErgPrincipal actor, AuditContext context) {
        Role role = roleRepository.findById(roleId)
                .or(() -> roleRepository.findByNameIgnoreCase(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        protectedRolePolicy.assertCanManageDefinition(actor, role);
        List<UserRef> affectedUsers = affectedUsers(role);
        String roleName = role.getName();
        roleRepository.delete(role);
        AuditContext requiredContext = requiredContext(actor, context);
        mutationAudit().recordRoleMutation(
                actorTenant(actor), role.getId(), "ROLE_DELETED", requiredContext,
                Map.of("roleName", roleName));
        mutationAudit().authorizationChanged(affectedUsers, role.getId(), "ROLE_DELETED", requiredContext);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "authorizationPermissions", key = "'all'")
    public List<PermissionResponse> listPermissions() {
        return permissionResponses(permissionRepository.findTop500ByOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public PermissionPageResponse searchPermissions(
            Integer page,
            Integer limit,
            String search,
            String group,
            String portal,
            String status,
            Boolean used
    ) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeLimit = limit == null ? 25 : Math.max(1, Math.min(100, limit));
        Page<Permission> result = permissionRepository.searchCatalog(
                trimToNull(search),
                trimToNull(group),
                trimToNull(portal),
                trimToNull(status),
                used,
                PageRequest.of(safePage, safeLimit, Sort.by(Sort.Direction.ASC, "name"))
        );
        return new PermissionPageResponse(
                permissionResponses(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermission(String permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseGet(() -> permissionRepository.findByNameInIgnoreCase(List.of(permissionId)).stream()
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("permission not found")));
        return permissionResponses(List.of(permission)).getFirst();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "authorizationPermissions", key = "'groups'")
    public List<PermissionGroupResponse> listPermissionGroups() {
        if (permissionGroupRepository == null) {
            throw new IllegalStateException("permission group repository is not configured");
        }
        List<PermissionGroup> groups = permissionGroupRepository.findAllByOrderByOrderAscNameAsc();
        Map<String, List<Permission>> permissionsByGroup = permissionRepository
                .findByGroupNameInOrderByGroupNameAscNameAsc(groups.stream().map(PermissionGroup::getName).toList())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Permission::getGroupName,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Map<String, PermissionResponse> responsesByPermissionId = permissionResponses(
                        permissionsByGroup.values().stream().flatMap(List::stream).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        PermissionResponse::id,
                        response -> response,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
        return groups.stream()
                .map(group -> new PermissionGroupResponse(
                        group.getId(),
                        group.getName(),
                        group.getLabel(),
                        group.getOrder() == null ? 0 : group.getOrder(),
                        permissionsByGroup.getOrDefault(group.getName(), List.of()).stream()
                                .map(permission -> responsesByPermissionId.get(permission.getId()))
                                .filter(java.util.Objects::nonNull)
                                .toList()
                ))
                .toList();
    }

    @Transactional
    public RoleResponse assignRoles(String userId, List<String> roleIds) {
        return assignRoles(userId, currentTenant(), roleIds, SecurityUtils.principal());
    }

    @Transactional
    public RoleResponse assignRoles(String userId, String tenantId, List<String> roleIds, ErgPrincipal actor) {
        return assignRoles(userId, tenantId, roleIds, actor, actorContext(actor));
    }

    @Transactional
    public RoleResponse assignRoles(
            String userId, String tenantId, List<String> roleIds, ErgPrincipal actor, AuditContext context
    ) {
        User user = userRepository.findByIdAndTenantId(userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        List<Role> roles = resolveRoles(roleIds);
        if (roles.size() != new LinkedHashSet<>(roleIds).size()) {
            throw new BadRequestException("one or more roles were not found");
        }
        protectedRolePolicy.assertCanGrant(actor, roles);
        user.setRoles(new LinkedHashSet<>(roles));
        userRepository.save(user);
        mutationAudit().authorizationChanged(
                List.of(new UserRef(user.getId(), normalizeTenant(tenantId))),
                roles.stream().map(Role::getId).sorted().collect(java.util.stream.Collectors.joining(",")),
                "DIRECT_ROLE_ASSIGNMENT_REPLACED",
                requiredContext(actor, context));
        return roles.stream().findFirst().map(this::toRoleResponse)
                .orElseThrow(() -> new BadRequestException("at least one role is required"));
    }

    @Transactional
    public void removeRole(String userId, String roleId) {
        removeRole(userId, roleId, currentTenant(), SecurityUtils.principal());
    }

    @Transactional
    public void removeRole(String userId, String roleId, String tenantId, ErgPrincipal actor) {
        removeRole(userId, roleId, tenantId, actor, actorContext(actor));
    }

    @Transactional
    public void removeRole(
            String userId, String roleId, String tenantId, ErgPrincipal actor, AuditContext context
    ) {
        User user = userRepository.findByIdAndTenantId(userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        Role role = roleRepository.findById(roleId)
                .or(() -> roleRepository.findByNameIgnoreCase(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        protectedRolePolicy.assertCanGrant(actor, List.of(role));
        Set<Role> roles = user.getRoles() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(user.getRoles());
        roles.removeIf(item -> item.getId().equals(role.getId()) || item.getName().equalsIgnoreCase(role.getName()));
        user.setRoles(roles);
        userRepository.save(user);
        mutationAudit().authorizationChanged(
                List.of(new UserRef(user.getId(), normalizeTenant(tenantId))),
                role.getId(),
                "DIRECT_ROLE_REMOVED",
                requiredContext(actor, context));
    }

    @Transactional
    public long bulkAssignRole(BulkAssignRoleRequest request) {
        return bulkAssignRole(request, currentTenant(), SecurityUtils.principal());
    }

    @Transactional
    public long bulkAssignRole(BulkAssignRoleRequest request, String tenantId, ErgPrincipal actor) {
        return bulkAssignRole(request, tenantId, actor, actorContext(actor));
    }

    @Transactional
    public long bulkAssignRole(
            BulkAssignRoleRequest request, String tenantId, ErgPrincipal actor, AuditContext context
    ) {
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        protectedRolePolicy.assertCanGrant(actor, List.of(role));
        List<User> users = userRepository.findByTenantIdAndIdInAnyStatus(
                normalizeTenant(tenantId), request.userIds());
        if (users.size() != new LinkedHashSet<>(request.userIds()).size()) {
            throw new ResourceNotFoundException("one or more users were not found in the authenticated tenant");
        }
        users.forEach(user -> {
            Set<Role> roles = user.getRoles() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(user.getRoles());
            roles.add(role);
            user.setRoles(roles);
        });
        userRepository.saveAll(users);
        mutationAudit().authorizationChanged(
                users.stream().map(user -> new UserRef(user.getId(), normalizeTenant(tenantId))).toList(),
                role.getId(),
                "DIRECT_ROLE_ASSIGNED",
                requiredContext(actor, context));
        return users.size();
    }

    @Transactional
    public PermissionOverrideResponse addPermissionOverride(String userId, AddPermissionOverrideRequest request, String actorId) {
        return addPermissionOverride(userId, request, actorId, currentTenant());
    }

    @Transactional
    public PermissionOverrideResponse addPermissionOverride(
            String userId, AddPermissionOverrideRequest request, String actorId, String tenantId
    ) {
        return addPermissionOverride(
                userId, request, actorId, tenantId, new AuditContext(actorId, null, null, null));
    }

    @Transactional
    public PermissionOverrideResponse addPermissionOverride(
            String userId,
            AddPermissionOverrideRequest request,
            String actorId,
            String tenantId,
            AuditContext context
    ) {
        scopedUser(userId, tenantId);
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
        PermissionOverrideResponse response = toOverrideResponse(overrideRepository.save(override));
        mutationAudit().authorizationChanged(
                List.of(new UserRef(userId, normalizeTenant(tenantId))),
                permission.getId(),
                "PERMISSION_OVERRIDE_ADDED",
                requiredContext(null, context));
        return response;
    }

    @Transactional
    public void deletePermissionOverride(String userId, String overrideId) {
        deletePermissionOverride(userId, overrideId, currentTenant());
    }

    @Transactional
    public void deletePermissionOverride(String userId, String overrideId, String tenantId) {
        ErgPrincipal actor = SecurityUtils.principal();
        deletePermissionOverride(userId, overrideId, tenantId, actorContext(actor));
    }

    @Transactional
    public void deletePermissionOverride(
            String userId, String overrideId, String tenantId, AuditContext context
    ) {
        scopedUser(userId, tenantId);
        UserPermissionOverride override = overrideRepository.findById(overrideId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("permission override not found"));
        String permissionId = permissionRepository.findByNameInIgnoreCase(List.of(override.getPermission())).stream()
                .findFirst().map(Permission::getId).orElse(override.getPermission());
        overrideRepository.delete(override);
        mutationAudit().authorizationChanged(
                List.of(new UserRef(userId, normalizeTenant(tenantId))),
                permissionId,
                "PERMISSION_OVERRIDE_DELETED",
                requiredContext(null, context));
    }

    @Transactional(readOnly = true)
    public List<PermissionOverrideResponse> listPermissionOverrides(String userId) {
        return listPermissionOverrides(userId, currentTenant());
    }

    @Transactional(readOnly = true)
    public List<PermissionOverrideResponse> listPermissionOverrides(String userId, String tenantId) {
        scopedUser(userId, tenantId);
        return overrideRepository.findByUserId(userId).stream().map(this::toOverrideResponse).toList();
    }

    @Transactional(readOnly = true)
    public EffectivePermissionsResponse effectivePermissions(String userId) {
        return effectivePermissions(userId, currentTenant());
    }

    @Transactional(readOnly = true)
    public EffectivePermissionsResponse effectivePermissions(String userId, String tenantId) {
        User user = userRepository.findWithDetailsByIdAndTenantId(userId, normalizeTenant(tenantId))
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
        return featureConfig(userId, currentTenant());
    }

    @Transactional(readOnly = true)
    public FeatureConfigResponse featureConfig(String userId, String tenantId) {
        EffectivePermissionsResponse permissions = effectivePermissions(userId, tenantId);
        User user = userRepository.findWithDetailsByIdAndTenantId(userId, normalizeTenant(tenantId))
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
        return previewChanges(userId, request, currentTenant());
    }

    @Transactional(readOnly = true)
    public PreviewChangesResponse previewChanges(String userId, PreviewChangesRequest request, String tenantId) {
        EffectivePermissionsResponse current = effectivePermissions(userId, tenantId);
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

    private List<UserRef> affectedUsers(Role role) {
        LinkedHashSet<UserRef> affected = new LinkedHashSet<>();
        if (role.getUsers() != null) {
            role.getUsers().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(user -> user.getId() != null && !user.getId().isBlank())
                    .map(user -> new UserRef(user.getId(), normalizeTenant(user.getTenantId())))
                    .forEach(affected::add);
        }
        if (userRoleAssignmentRepository != null) {
            userRoleAssignmentRepository.findActiveForRole(role.getId(), OffsetDateTime.now()).stream()
                    .filter(java.util.Objects::nonNull)
                    .map(assignment -> new UserRef(
                            assignment.getUserId(), normalizeTenant(assignment.getTenantId())))
                    .forEach(affected::add);
        }
        return List.copyOf(affected);
    }

    private AuthorizationMutationAuditService mutationAudit() {
        if (mutationAuditService == null) {
            throw new IllegalStateException("authorization mutation audit service is not configured");
        }
        return mutationAuditService;
    }

    private AuditContext requiredContext(ErgPrincipal actor, AuditContext context) {
        AuditContext resolved = context == null ? actorContext(actor) : context;
        if (resolved.actorUserId() == null || resolved.actorUserId().isBlank()) {
            throw new BadRequestException("authorization mutation actor is required");
        }
        return resolved;
    }

    private AuditContext actorContext(ErgPrincipal actor) {
        return new AuditContext(actor == null ? null : actor.userId(), null, null, null);
    }

    private String actorTenant(ErgPrincipal actor) {
        return normalizeTenant(actor == null ? null : actor.tenantId());
    }

    private RoleResponse toRoleResponse(Role role) {
        return toRoleResponse(role, activeAssignmentsByRole(List.of(role)).getOrDefault(role.getId(), List.of()));
    }

    private RoleResponse toRoleResponse(
            Role role,
            List<com.erg.ergspringboot.model.authorization.UserRoleAssignment> scopedAssignments
    ) {
        LinkedHashSet<String> userIds = role.getUsers() == null
                ? new LinkedHashSet<>()
                : role.getUsers().stream().map(User::getId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        scopedAssignments.stream().map(com.erg.ergspringboot.model.authorization.UserRoleAssignment::getUserId)
                .filter(java.util.Objects::nonNull).forEach(userIds::add);
        List<String> scopeTypes = scopedAssignments.stream()
                .map(com.erg.ergspringboot.model.authorization.UserRoleAssignment::getScopeType)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        boolean systemRole = Boolean.TRUE.equals(role.getIsDefault())
                || (role.getName() != null && role.getName().startsWith("system."));
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions() == null ? List.of() : role.getPermissions().stream().map(Permission::getName).toList(),
                Boolean.TRUE.equals(role.getIsDefault()),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                userIds.size(),
                scopeTypes,
                systemRole,
                "ACTIVE",
                role.getUpdatedBy()
        );
    }

    private Map<String, List<com.erg.ergspringboot.model.authorization.UserRoleAssignment>> activeAssignmentsByRole(
            List<Role> roles
    ) {
        if (userRoleAssignmentRepository == null || roles.isEmpty()) {
            return Map.of();
        }
        return userRoleAssignmentRepository.findActiveForRoleIds(
                        roles.stream().map(Role::getId).filter(java.util.Objects::nonNull).toList(),
                        OffsetDateTime.now(ZoneOffset.UTC))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.erg.ergspringboot.model.authorization.UserRoleAssignment::getRoleId,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    private List<PermissionResponse> permissionResponses(List<Permission> permissions) {
        if (permissions.isEmpty()) {
            return List.of();
        }
        List<String> permissionIds = permissions.stream().map(Permission::getId).toList();
        List<String> permissionNames = permissions.stream().map(Permission::getName).toList();
        Map<String, List<PermissionBinding>> bindingsByPermissionId = permissionBindingRepository == null
                ? Map.of()
                : permissionBindingRepository.findByPermissionIdInOrderByPermissionIdAscSurfaceTypeAscSurfaceIdentifierAsc(permissionIds)
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                PermissionBinding::getPermissionId,
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));
        Map<String, List<Role>> rolesByPermissionId = roleRepository
                .findAllWithPermissionsAndUsersByPermissionIds(permissionIds)
                .stream()
                .flatMap(role -> role.getPermissions().stream()
                        .filter(permission -> permissionIds.contains(permission.getId()))
                        .map(permission -> Map.entry(permission.getId(), role)))
                .collect(java.util.stream.Collectors.groupingBy(
                        Map.Entry::getKey,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(Map.Entry::getValue, java.util.stream.Collectors.toList())));
        Map<String, List<UserPermissionOverride>> overridesByPermissionName = overrideRepository.findByPermissionIn(permissionNames)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        UserPermissionOverride::getPermission,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        return permissions.stream()
                .map(permission -> toPermissionResponse(
                        permission,
                        rolesByPermissionId.getOrDefault(permission.getId(), List.of()),
                        bindingsByPermissionId.getOrDefault(permission.getId(), List.of()),
                        overridesByPermissionName.getOrDefault(permission.getName(), List.of())))
                .toList();
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        return toPermissionResponse(
                permission,
                permission.getRoles() == null ? List.of() : List.copyOf(permission.getRoles()),
                permission.getBindings() == null ? List.of() : List.copyOf(permission.getBindings()),
                permission.getUserOverrides() == null ? List.of() : List.copyOf(permission.getUserOverrides()));
    }

    private PermissionResponse toPermissionResponse(
            Permission permission,
            List<Role> roles,
            List<PermissionBinding> permissionBindings,
            List<UserPermissionOverride> overrides
    ) {
        List<PermissionBindingResponse> bindings = permissionBindings.stream()
                .sorted(java.util.Comparator.comparing(PermissionBinding::getSurfaceType, java.util.Comparator.nullsLast(String::compareTo))
                        .thenComparing(PermissionBinding::getSurfaceIdentifier, java.util.Comparator.nullsLast(String::compareTo)))
                .map(binding -> new PermissionBindingResponse(
                        binding.getId(),
                        binding.getSurfaceType(),
                        binding.getSurfaceIdentifier(),
                        binding.getDescription(),
                        Boolean.TRUE.equals(binding.getEnforced())
                ))
                .toList();
        int roleCount = roles.size();
        LinkedHashSet<String> permissionUserIds = roles.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(role -> role.getUsers() == null ? java.util.stream.Stream.empty() : role.getUsers().stream())
                .filter(java.util.Objects::nonNull)
                .map(User::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        overrides.stream().map(UserPermissionOverride::getUserId)
                .filter(java.util.Objects::nonNull).forEach(permissionUserIds::add);
        List<String> roleNames = roles.stream()
                .map(Role::getName).filter(java.util.Objects::nonNull).sorted().toList();
        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getGroupName(),
                permission.getLabel(),
                permission.getDescription(),
                permission.getResource(),
                permission.getAction(),
                permission.getPortal(),
                permission.getSupportedScope(),
                permission.getStatus(),
                Boolean.TRUE.equals(permission.getIsSystem()),
                permission.getDeprecatedAt(),
                bindings.stream().anyMatch(PermissionBindingResponse::enforced)
                        || roleCount > 0
                        || !overrides.isEmpty(),
                roleCount,
                permissionUserIds.size(),
                roleNames,
                bindings
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

    private User scopedUser(String userId, String tenantId) {
        return userRepository.findByIdAndTenantId(userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    private String currentTenant() {
        ErgPrincipal principal = SecurityUtils.principal();
        return principal == null ? "erg" : normalizeTenant(principal.tenantId());
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "erg" : tenantId.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
