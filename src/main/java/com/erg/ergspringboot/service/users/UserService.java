package com.erg.ergspringboot.service.users;

import com.erg.ergspringboot.common.request.users.UserRequests.OnboardingRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignPermissionsRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignRolesRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminCreateUserRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminUpdateUserStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkDeleteRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.ChangePasswordRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.ListUsersQuery;
import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.OnboardingResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.BulkOperationResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.Meta;
import com.erg.ergspringboot.common.response.users.UserResponses.PermissionOverrideResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RevokeSessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SocialAccountResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserDetailResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserItemResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.SocialAccount;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.users.SocialAccountRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.auth.PasswordService;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String DEFAULT_TENANT = "erg";

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository permissionOverrideRepository;
    private final UserActivityRepository userActivityRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordService passwordService;

    public UserService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserPermissionOverrideRepository permissionOverrideRepository,
            UserActivityRepository userActivityRepository,
            SocialAccountRepository socialAccountRepository,
            PasswordService passwordService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionOverrideRepository = permissionOverrideRepository;
        this.userActivityRepository = userActivityRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.passwordService = passwordService;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId, String tenantId) {
        return toResponse(findUser(userId, tenantId));
    }

    @Transactional
    public UserResponse updateProfile(String userId, String tenantId, UpdateProfileRequest request) {
        User user = findUser(userId, tenantId);
        applyProfileUpdate(user, request);
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public OnboardingResponse completeOnboarding(String userId, String tenantId, OnboardingRequest request) {
        User user = findUser(userId, tenantId);
        user.setFullName(trimToNull(request.fullName()));
        user.setPhone(trimToNull(request.phone()));
        user.setBio(trimToNull(request.bio()));
        user.setGender(trimToNull(request.gender()));
        user.setDateOfBirth(trimToNull(request.dateOfBirth()));
        user.setAddress(trimToNull(request.address()));
        user.setCity(trimToNull(request.city()));
        user.setDistrict(trimToNull(request.district()));
        user.setJobTitle(trimToNull(request.jobTitle()));
        user.setRegion(trimToNull(request.region()));
        user.setIsProfileCompleted(true);
        userRepository.save(user);
        return new OnboardingResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                Boolean.TRUE.equals(user.getIsProfileCompleted())
        );
    }

    @Transactional(readOnly = true)
    public SessionListResponse listSessions(String userId, String tenantId, String currentSessionId) {
        List<SessionResponse> items = sessionRepository
                .findByUserIdAndTenantIdOrderByLastActiveAtDesc(userId, normalizeTenant(tenantId))
                .stream()
                .map(session -> toSessionResponse(session, currentSessionId))
                .toList();
        return new SessionListResponse(items);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public RevokeSessionResponse revokeSession(String userId, String tenantId, String sessionId) {
        AuthSession session = sessionRepository.findBySessionIdAndUserIdAndTenantId(sessionId, userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        OffsetDateTime revokedAt = now();
        session.setRevokedAt(revokedAt);
        session.setRevokedReason("user_revoked");
        sessionRepository.save(session);
        return new RevokeSessionResponse(true, toIso(revokedAt));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public void changePassword(String userId, String tenantId, ChangePasswordRequest request) {
        User user = findUser(userId, tenantId);
        if (!passwordService.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BadRequestException("invalid old password");
        }
        user.setPasswordHash(passwordService.hash(request.newPassword()));
        userRepository.save(user);

        List<AuthSession> sessions = sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(userId, normalizeTenant(tenantId));
        OffsetDateTime revokedAt = now();
        sessions.forEach(session -> {
            session.setRevokedAt(revokedAt);
            session.setRevokedReason("password_changed");
        });
        sessionRepository.saveAll(sessions);
    }

    @Transactional(readOnly = true)
    public UserListResponse listUsers(Integer page, Integer limit, String search, String status, String role) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        List<User> filtered = userRepository.findAll().stream()
                .filter(user -> matchesSearch(user, search))
                .filter(user -> isBlank(status) || status.equalsIgnoreCase(user.getStatus()))
                .filter(user -> isBlank(role) || roles(user.getRoles()).stream().anyMatch(role::equalsIgnoreCase))
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int from = Math.min((safePage - 1) * safeLimit, filtered.size());
        int to = Math.min(from + safeLimit, filtered.size());
        List<UserItemResponse> users = filtered.subList(from, to).stream()
                .map(this::toItemResponse)
                .toList();
        long totalPages = filtered.isEmpty() ? 0 : (long) Math.ceil((double) filtered.size() / safeLimit);
        return new UserListResponse(users, new Meta(safePage, safeLimit, filtered.size(), totalPages));
    }

    public UserListResponse listUsers(ListUsersQuery query) {
        return listUsers(query.page(), query.limit(), query.search(), query.status(), query.role());
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(String userId, String tenantId) {
        User user = findUser(userId, tenantId);
        List<SessionResponse> sessions = sessionRepository
                .findByUserIdAndTenantIdOrderByLastActiveAtDesc(userId, normalizeTenant(tenantId))
                .stream()
                .map(session -> toSessionResponse(session, null))
                .toList();
        List<SocialAccountResponse> socialAccounts = socialAccountRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toSocialAccountResponse)
                .toList();
        return new UserDetailResponse(toResponse(user), sessions, socialAccounts);
    }

    @Transactional(readOnly = true)
    public ActivityListResponse listActivity(String userId, String tenantId, Integer page, Integer limit) {
        findUser(userId, tenantId);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        List<UserActivity> activities = userActivityRepository
                .findByTargetUserIdAndTenantIdOrderByCreatedAtDesc(userId, normalizeTenant(tenantId));
        int from = Math.min((safePage - 1) * safeLimit, activities.size());
        int to = Math.min(from + safeLimit, activities.size());
        List<ActivityResponse> items = activities.subList(from, to).stream()
                .map(this::toActivityResponse)
                .toList();
        long totalPages = activities.isEmpty() ? 0 : (long) Math.ceil((double) activities.size() / safeLimit);
        return new ActivityListResponse(items, new Meta(safePage, safeLimit, activities.size(), totalPages));
    }

    @Transactional
    public UserCreatedResponse createUser(AdminCreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("email already registered");
        }
        User user = new User();
        user.setId(IdGenerator.newId());
        user.setEmail(email);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setFullName(trimToNull(request.fullName()));
        user.setPhone(trimToNull(request.phone()));
        user.setStatus("ACTIVE");
        user.setProvider("local");
        user.setAccountType("erg");
        user.setTenantId(DEFAULT_TENANT);
        user.setIsProfileCompleted(isProfileCompleted(user));
        user.setLoginCount(0L);
        userRepository.save(user);
        return new UserCreatedResponse(user.getId(), user.getEmail(), user.getFullName(), user.getStatus());
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public UserResponse updateUserStatus(String userId, String tenantId, AdminUpdateUserStatusRequest request) {
        String status = normalizeStatus(request.status());
        User user = findUser(userId, tenantId);
        user.setStatus(status);
        userRepository.save(user);
        revokeActiveSessionsIfLocked(user, normalizeTenant(tenantId), status);
        return toResponse(user);
    }

    @Transactional
    public BulkOperationResponse deleteUser(String userId, String tenantId) {
        User user = findUser(userId, tenantId);
        userRepository.delete(user);
        return new BulkOperationResponse(1);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public BulkOperationResponse bulkUpdateStatus(String tenantId, BulkStatusRequest request) {
        String status = normalizeStatus(request.status());
        List<User> users = findUsers(request.userIds(), tenantId);
        users.forEach(user -> user.setStatus(status));
        userRepository.saveAll(users);
        String normalizedTenant = normalizeTenant(tenantId);
        users.forEach(user -> revokeActiveSessionsIfLocked(user, normalizedTenant, status));
        return new BulkOperationResponse(users.size());
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public BulkOperationResponse bulkDelete(String tenantId, BulkDeleteRequest request) {
        List<User> users = findUsers(request.userIds(), tenantId);
        userRepository.deleteAll(users);
        return new BulkOperationResponse(users.size());
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public UserResponse assignRoles(String userId, String tenantId, AdminAssignRolesRequest request) {
        User user = findUser(userId, tenantId);
        List<String> roleNames = request.roles().stream()
                .map(this::trimToNull)
                .filter(name -> name != null)
                .toList();
        List<Role> roles = roleRepository.findByNameInIgnoreCase(roleNames);
        if (roles.size() != new LinkedHashSet<>(roleNames).size()) {
            throw new BadRequestException("one or more roles were not found");
        }
        user.setRoles(new LinkedHashSet<>(roles));
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public PermissionOverrideResponse assignPermissions(String userId, String tenantId, AdminAssignPermissionsRequest request) {
        findUser(userId, tenantId);
        String grantType = normalizeGrantType(request.grantType());
        List<String> permissionNames = request.permissions().stream()
                .map(this::trimToNull)
                .filter(name -> name != null)
                .toList();
        List<Permission> permissions = permissionRepository.findByNameInIgnoreCase(permissionNames);
        if (permissions.size() != new LinkedHashSet<>(permissionNames).size()) {
            throw new BadRequestException("one or more permissions were not found");
        }
        permissionOverrideRepository.deleteByUserId(userId);
        List<UserPermissionOverride> overrides = permissions.stream()
                .map(permission -> {
                    UserPermissionOverride override = new UserPermissionOverride();
                    override.setId(IdGenerator.newId());
                    override.setUserId(userId);
                    override.setPermission(permission.getName());
                    override.setGrantType(grantType);
                    override.setReason(trimToNull(request.reason()));
                    override.setExpiresAt(parseOptionalTimestamp(request.expiresAt()));
                    override.setCreatedAt(now());
                    return override;
                })
                .toList();
        permissionOverrideRepository.saveAll(overrides);
        return new PermissionOverrideResponse(userId, permissions.stream().map(Permission::getName).toList(), grantType);
    }

    private User findUser(String userId, String tenantId) {
        return userRepository.findByIdAndTenantId(userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    private List<User> findUsers(List<String> userIds, String tenantId) {
        return userIds.stream()
                .map(userId -> findUser(userId, tenantId))
                .toList();
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase();
        if (!List.of("ACTIVE", "PENDING", "BLOCKED", "BANNED", "INACTIVE").contains(normalized)) {
            throw new BadRequestException("invalid user status");
        }
        return normalized;
    }

    private void revokeActiveSessionsIfLocked(User user, String tenantId, String status) {
        if (!List.of("BLOCKED", "BANNED", "INACTIVE").contains(status)) {
            return;
        }
        List<AuthSession> sessions = sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(user.getId(), tenantId);
        if (sessions.isEmpty()) {
            return;
        }
        OffsetDateTime revokedAt = now();
        String reason = "status_changed_" + status.toLowerCase();
        sessions.forEach(session -> {
            session.setRevokedAt(revokedAt);
            session.setRevokedReason(reason);
        });
        sessionRepository.saveAll(sessions);
    }

    private String normalizeGrantType(String grantType) {
        String normalized = grantType.trim().toUpperCase();
        if (!List.of("GRANT", "DENY").contains(normalized)) {
            throw new BadRequestException("invalid permission grant type");
        }
        return normalized;
    }

    private void applyProfileUpdate(User user, UpdateProfileRequest request) {
        if (request.fullName() != null) {
            user.setFullName(trimToNull(request.fullName()));
        }
        if (request.phone() != null) {
            user.setPhone(trimToNull(request.phone()));
        }
        if (request.bio() != null) {
            user.setBio(trimToNull(request.bio()));
        }
        if (request.gender() != null) {
            user.setGender(trimToNull(request.gender()));
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(trimToNull(request.dateOfBirth()));
        }
        if (request.address() != null) {
            user.setAddress(trimToNull(request.address()));
        }
        if (request.city() != null) {
            user.setCity(trimToNull(request.city()));
        }
        if (request.district() != null) {
            user.setDistrict(trimToNull(request.district()));
        }
        if (request.jobTitle() != null) {
            user.setJobTitle(trimToNull(request.jobTitle()));
        }
        if (request.region() != null) {
            user.setRegion(trimToNull(request.region()));
        }
        if (request.socialLinks() != null) {
            user.setSocialLinks(request.socialLinks());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(trimToNull(request.avatarUrl()));
        }
        user.setIsProfileCompleted(isProfileCompleted(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getGender(),
                user.getDateOfBirth(),
                user.getAddress(),
                user.getCity(),
                user.getDistrict(),
                user.getJobTitle(),
                user.getRegion(),
                user.getStatus(),
                user.getProvider(),
                user.getAccountType(),
                user.getGoogleSub(),
                user.getGoogleEmail(),
                user.getGoogleEmailVerified(),
                user.getLastLoginProvider(),
                roles(user.getRoles()),
                user.getSocialLinks() == null ? Map.of() : user.getSocialLinks(),
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                toIso(user.getLastLoginAt()),
                user.getLoginCount(),
                user.getTenantId(),
                toIso(user.getCreatedAt()),
                toIso(user.getUpdatedAt())
        );
    }

    private UserItemResponse toItemResponse(User user) {
        return new UserItemResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getStatus(),
                user.getProvider(),
                user.getAccountType(),
                user.getLastLoginProvider(),
                roles(user.getRoles()),
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                toIso(user.getLastLoginAt()),
                toIso(user.getCreatedAt())
        );
    }

    private SessionResponse toSessionResponse(AuthSession session, String currentSessionId) {
        OffsetDateTime lastSeenAt = session.getLastActiveAt() == null ? session.getCreatedAt() : session.getLastActiveAt();
        return new SessionResponse(
                session.getSessionId(),
                session.getDeviceId(),
                session.getDeviceName(),
                session.getPortal(),
                session.getIpAddress(),
                session.getUserAgent(),
                deviceType(session.getUserAgent()),
                session.getSessionId() != null && session.getSessionId().equals(currentSessionId),
                session.getRevokedAt() != null,
                session.getRevokedReason(),
                toIso(session.getExpiresAt()),
                toIso(session.getCreatedAt()),
                toIso(lastSeenAt)
        );
    }

    private SocialAccountResponse toSocialAccountResponse(SocialAccount socialAccount) {
        return new SocialAccountResponse(
                socialAccount.getId(),
                socialAccount.getProvider(),
                socialAccount.getProviderId(),
                socialAccount.getEmail(),
                toIso(socialAccount.getCreatedAt())
        );
    }

    private ActivityResponse toActivityResponse(UserActivity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getUserId(),
                activity.getAction(),
                activity.getTargetUserId(),
                activity.getIpAddress(),
                activity.getDescription(),
                activity.getMetadata() == null ? Map.of() : activity.getMetadata(),
                toIso(activity.getCreatedAt())
        );
    }

    private boolean matchesSearch(User user, String search) {
        if (isBlank(search)) {
            return true;
        }
        String needle = search.toLowerCase();
        return contains(user.getEmail(), needle) || contains(user.getFullName(), needle) || contains(user.getPhone(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private String deviceType(String userAgent) {
        if (userAgent == null) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        }
        if (ua.contains("ipad")) {
            return "tablet";
        }
        if (ua.contains("curl") || ua.contains("http")) {
            return "api";
        }
        return "desktop";
    }

    private List<String> roles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("user");
        }
        return roles.stream()
                .map(Role::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
    }

    private boolean isProfileCompleted(User user) {
        return !isBlank(user.getFullName()) && !isBlank(user.getPhone());
    }

    private String normalizeTenant(String tenantId) {
        return isBlank(tenantId) ? DEFAULT_TENANT : tenantId.trim();
    }

    private String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String toIso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime parseOptionalTimestamp(String value) {
        return isBlank(value) ? null : OffsetDateTime.parse(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

