package com.erg.ergspringboot.users.internal.profile;

import com.erg.ergspringboot.common.request.users.UserRequests.OnboardingRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.OnboardingResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns profile and onboarding mutations for the users module. */
@Service
public class UserProfileUseCase {
    private static final String DEFAULT_TENANT = "erg";

    private final UserRepository userRepository;
    private final UserActivityRepository activityRepository;

    public UserProfileUseCase(UserRepository userRepository, UserActivityRepository activityRepository) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional
    public UserResponse update(
            String userId, String tenantId, UpdateProfileRequest request, UserAuditContext auditContext
    ) {
        User user = findUser(userId, tenantId);
        Map<String, Object> before = profileAuditState(user);
        apply(user, request);
        userRepository.save(user);
        record(user, auditContext, "ACCOUNT_PROFILE_UPDATED", "Account profile updated",
                before, profileAuditState(user));
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
        return new OnboardingResponse(user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(), true);
    }

    private void apply(User user, UpdateProfileRequest request) {
        if (request.fullName() != null) user.setFullName(trimToNull(request.fullName()));
        if (request.phone() != null) user.setPhone(trimToNull(request.phone()));
        if (request.bio() != null) user.setBio(trimToNull(request.bio()));
        if (request.gender() != null) user.setGender(trimToNull(request.gender()));
        if (request.dateOfBirth() != null) user.setDateOfBirth(trimToNull(request.dateOfBirth()));
        if (request.address() != null) user.setAddress(trimToNull(request.address()));
        if (request.city() != null) user.setCity(trimToNull(request.city()));
        if (request.district() != null) user.setDistrict(trimToNull(request.district()));
        if (request.jobTitle() != null) user.setJobTitle(trimToNull(request.jobTitle()));
        if (request.region() != null) user.setRegion(trimToNull(request.region()));
        if (request.socialLinks() != null) user.setSocialLinks(request.socialLinks());
        if (request.avatarUrl() != null) user.setAvatarUrl(trimToNull(request.avatarUrl()));
        user.setIsProfileCompleted(!isBlank(user.getFullName()) && !isBlank(user.getPhone()));
    }

    private void record(User target, UserAuditContext audit, String action, String description,
            Map<String, Object> before, Map<String, Object> after) {
        UserAuditContext context = audit == null
                ? new UserAuditContext(target.getId(), null, null, null) : audit;
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setUserId(isBlank(context.actorUserId()) ? target.getId() : context.actorUserId());
        activity.setTenantId(normalizeTenant(target.getTenantId()));
        activity.setAction(action);
        activity.setTargetUserId(target.getId());
        activity.setIpAddress(trimToNull(context.ipAddress()));
        activity.setUserAgent(trimToNull(context.userAgent()));
        activity.setDescription(description);
        activity.setRequestId(trimToNull(context.requestId()));
        activity.setMetadata(Map.of("before", before, "after", after));
        activity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        activityRepository.save(activity);
    }

    private User findUser(String userId, String tenantId) {
        return userRepository.findByIdAndTenantId(userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getBio(), user.getGender(), user.getDateOfBirth(), user.getAddress(),
                user.getCity(), user.getDistrict(), user.getJobTitle(), user.getRegion(), user.getStatus(),
                user.getProvider(), user.getAccountType(), user.getLastLoginProvider(), roles(user.getRoles()),
                user.getSocialLinks() == null ? Map.of() : user.getSocialLinks(),
                Boolean.TRUE.equals(user.getIsProfileCompleted()), toIso(user.getLastLoginAt()), user.getLoginCount(),
                user.getTenantId(), toIso(user.getCreatedAt()), toIso(user.getUpdatedAt()), lifecycle(user));
    }

    private AccountLifecycleResponse lifecycle(User user) {
        List<String> nextSteps = new ArrayList<>();
        if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) nextSteps.add("COMPLETE_PROFILE");
        if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) nextSteps.add("VERIFY_RECOVERY_EMAIL");
        if (Boolean.TRUE.equals(user.getMustChangePassword())) nextSteps.add("CHANGE_PASSWORD");
        String status = nextSteps.isEmpty() ? "ACTIVE"
                : ("INVITED".equalsIgnoreCase(user.getStatus()) ? "PROVISIONED" : "FIRST_LOGIN_RESTRICTED");
        return new AccountLifecycleResponse(status, Boolean.TRUE.equals(user.getIsProfileCompleted()),
                maskEmail(user.getRecoveryEmail()), Boolean.TRUE.equals(user.getRecoveryEmailVerified()),
                Boolean.TRUE.equals(user.getMustChangePassword()), List.copyOf(nextSteps));
    }

    private Map<String, Object> profileAuditState(User user) {
        return Map.of("fullName", safe(user.getFullName()), "phone", safe(user.getPhone()),
                "isProfileCompleted", Boolean.TRUE.equals(user.getIsProfileCompleted()));
    }

    private List<String> roles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) return List.of("user");
        return roles.stream().map(Role::getName).filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return null;
        return email.substring(0, 1) + "***" + email.substring(email.indexOf('@'));
    }

    private String normalizeTenant(String tenantId) {
        return isBlank(tenantId) ? DEFAULT_TENANT : tenantId.trim();
    }

    private String trimToNull(String value) { return isBlank(value) ? null : value.trim(); }
    private String safe(String value) { return value == null ? "" : value; }
    private String toIso(OffsetDateTime value) { return value == null ? null : value.toString(); }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
