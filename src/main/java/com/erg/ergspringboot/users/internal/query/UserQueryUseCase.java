package com.erg.ergspringboot.users.internal.query;

import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.Meta;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserDetailResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserItemResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.auth.user.projection.UserListItemProjection;
import com.erg.ergspringboot.repository.auth.user.projection.UserRoleNameProjection;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only user use cases backed by tenant-scoped projections. */
@Service
public class UserQueryUseCase {

    private static final String DEFAULT_TENANT = "erg";

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final UserActivityRepository userActivityRepository;

    public UserQueryUseCase(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            UserActivityRepository userActivityRepository
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.userActivityRepository = userActivityRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse current(String userId, String tenantId) {
        return toResponse(findUser(userId, tenantId));
    }

    @Transactional(readOnly = true)
    public UserListResponse list(
            String tenantId, Integer page, Integer limit, String search, String status, String role
    ) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        Page<UserListItemProjection> pageItems = userRepository.findAdminListItems(
                normalizeTenant(tenantId),
                trimToNull(search),
                trimToNull(status),
                trimToNull(role),
                PageRequest.of(safePage - 1, safeLimit)
        );
        List<String> userIds = pageItems.getContent().stream()
                .map(UserListItemProjection::id)
                .toList();
        Map<String, List<String>> rolesByUserId = userIds.isEmpty()
                ? Map.of()
                : userRepository.findRoleNamesByUserIds(userIds).stream()
                        .collect(Collectors.groupingBy(
                                UserRoleNameProjection::userId,
                                Collectors.mapping(UserRoleNameProjection::roleName, Collectors.toList())
                        ));
        List<UserItemResponse> users = pageItems.getContent().stream()
                .map(user -> toItemResponse(user, rolesByUserId.get(user.id())))
                .toList();
        return new UserListResponse(
                users,
                new Meta(safePage, safeLimit, pageItems.getTotalElements(), pageItems.getTotalPages())
        );
    }

    @Transactional(readOnly = true)
    public UserDetailResponse detail(String userId, String tenantId) {
        User user = findUser(userId, tenantId);
        List<SessionResponse> sessions = sessionRepository
                .findByUserIdAndTenantIdOrderByLastActiveAtDesc(userId, normalizeTenant(tenantId))
                .stream()
                .map(session -> toSessionResponse(session, null))
                .toList();
        return new UserDetailResponse(toResponse(user), sessions);
    }

    @Transactional(readOnly = true)
    public ActivityListResponse activity(String userId, String tenantId, Integer page, Integer limit) {
        findUser(userId, tenantId);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        Page<UserActivity> activities = userActivityRepository
                .findByTargetUserIdAndTenantIdOrderByCreatedAtDescIdDesc(
                        userId, normalizeTenant(tenantId), PageRequest.of(safePage - 1, safeLimit));
        List<ActivityResponse> items = activities.getContent().stream()
                .map(this::toActivityResponse)
                .toList();
        return new ActivityListResponse(items, new Meta(
                safePage, safeLimit, activities.getTotalElements(), activities.getTotalPages()));
    }

    private User findUser(String userId, String tenantId) {
        return userRepository.findByIdAndTenantId(userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getAvatarUrl(),
                user.getBio(), user.getGender(), user.getDateOfBirth(), user.getAddress(), user.getCity(),
                user.getDistrict(), user.getJobTitle(), user.getRegion(), user.getStatus(), user.getProvider(),
                user.getAccountType(), user.getLastLoginProvider(), roles(user.getRoles()),
                user.getSocialLinks() == null ? Map.of() : user.getSocialLinks(),
                Boolean.TRUE.equals(user.getIsProfileCompleted()), toIso(user.getLastLoginAt()),
                user.getLoginCount(), user.getTenantId(), toIso(user.getCreatedAt()), toIso(user.getUpdatedAt()),
                lifecycle(user)
        );
    }

    private UserItemResponse toItemResponse(UserListItemProjection user, List<String> roles) {
        return new UserItemResponse(
                user.id(), user.email(), user.fullName(), user.avatarUrl(), user.phone(), user.status(),
                user.provider(), user.accountType(), user.lastLoginProvider(), roleNames(roles),
                Boolean.TRUE.equals(user.isProfileCompleted()), toIso(user.lastLoginAt()), toIso(user.createdAt())
        );
    }

    private SessionResponse toSessionResponse(AuthSession session, String currentSessionId) {
        OffsetDateTime lastSeenAt = session.getLastActiveAt() == null ? session.getCreatedAt() : session.getLastActiveAt();
        return new SessionResponse(
                session.getSessionId(), session.getDeviceId(), session.getDeviceName(), session.getPortal(),
                session.getIpAddress(), session.getUserAgent(), deviceType(session.getUserAgent()),
                session.getSessionId() != null && session.getSessionId().equals(currentSessionId),
                session.getRevokedAt() != null, session.getRevokedReason(), toIso(session.getExpiresAt()),
                toIso(session.getCreatedAt()), toIso(lastSeenAt)
        );
    }

    private ActivityResponse toActivityResponse(UserActivity activity) {
        return new ActivityResponse(
                activity.getId(), activity.getUserId(), activity.getAction(), activity.getTargetUserId(),
                activity.getIpAddress(), activity.getDescription(),
                activity.getMetadata() == null ? Map.of() : activity.getMetadata(), toIso(activity.getCreatedAt())
        );
    }

    private AccountLifecycleResponse lifecycle(User user) {
        List<String> nextSteps = new ArrayList<>();
        if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) nextSteps.add("COMPLETE_PROFILE");
        if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) nextSteps.add("VERIFY_RECOVERY_EMAIL");
        if (Boolean.TRUE.equals(user.getMustChangePassword())) nextSteps.add("CHANGE_PASSWORD");
        String lifecycleStatus = nextSteps.isEmpty()
                ? "ACTIVE"
                : ("INVITED".equalsIgnoreCase(user.getStatus()) ? "PROVISIONED" : "FIRST_LOGIN_RESTRICTED");
        return new AccountLifecycleResponse(
                lifecycleStatus, Boolean.TRUE.equals(user.getIsProfileCompleted()), maskEmail(user.getRecoveryEmail()),
                Boolean.TRUE.equals(user.getRecoveryEmailVerified()), Boolean.TRUE.equals(user.getMustChangePassword()),
                List.copyOf(nextSteps)
        );
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return null;
        int separator = email.indexOf('@');
        return email.substring(0, 1) + "***" + email.substring(separator);
    }

    private String deviceType(String userAgent) {
        if (userAgent == null) return "unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "mobile";
        if (ua.contains("ipad")) return "tablet";
        if (ua.contains("curl") || ua.contains("http")) return "api";
        return "desktop";
    }

    private List<String> roles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) return List.of("user");
        return roles.stream().map(Role::getName)
                .filter(name -> name != null && !name.isBlank()).collect(Collectors.toList());
    }

    private List<String> roleNames(List<String> roles) {
        if (roles == null || roles.isEmpty()) return List.of("user");
        return roles.stream().filter(name -> name != null && !name.isBlank()).collect(Collectors.toList());
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toIso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
