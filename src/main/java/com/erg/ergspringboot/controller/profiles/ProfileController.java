package com.erg.ergspringboot.controller.profiles;

import com.erg.ergspringboot.common.request.profiles.ProfileRequests.CreateProfileRequest;
import com.erg.ergspringboot.common.request.profiles.ProfileRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.profiles.ProfileResponses.ProfileResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.service.profiles.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profileService;
    private final ErgPolicyService policyService;

    public ProfileController(ProfileService profileService, ErgPolicyService policyService) {
        this.profileService = profileService;
        this.policyService = policyService;
    }

    @GetMapping("/{userId}")
    public ApiResponse<ProfileResponse> getProfile(@PathVariable String userId, HttpServletRequest request) {
        ErgPrincipal principal = requireOwnProfileOrManage(userId);
        return ok("Profile loaded",
                profileService.getProfile(userId, principal.tenantId(), seesInternalNote(principal, userId)), request);
    }

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getCurrentProfile(HttpServletRequest request) {
        return ok("Profile loaded", profileService.getProfile(currentUserId(), SecurityUtils.requirePrincipal().tenantId(), false), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProfileResponse> createProfile(@Valid @RequestBody CreateProfileRequest body, HttpServletRequest request) {
        ErgPrincipal principal = requireOwnProfileOrManage(body.userId());
        return ApiResponse.success(201, "Profile created",
                profileService.createProfile(body, principal.tenantId(), seesInternalNote(principal, body.userId())),
                request.getRequestURI(), requestId(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<ProfileResponse> updateProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateProfileRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = requireOwnProfileOrManage(userId);
        return ok("Profile updated",
                profileService.updateProfile(userId, body, principal.tenantId(), seesInternalNote(principal, userId)), request);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteProfile(@PathVariable String userId, HttpServletRequest request) {
        ErgPrincipal principal = requireOwnProfileOrManage(userId);
        profileService.deleteProfile(userId, principal.tenantId());
        return ok("Profile deleted", null, request);
    }

    private ErgPrincipal requireOwnProfileOrManage(String userId) {
        ErgPrincipal principal = SecurityUtils.requirePrincipal();
        if (!userId.equals(principal.userId()) && !canManage(principal)) {
            throw new ForbiddenException("You can only access your own profile");
        }
        return principal;
    }

    private boolean canManage(ErgPrincipal principal) {
        return policyService.isAllowed(principal, "users.manage", null, principal.tenantId());
    }

    /** Only a manager looking at someone else's profile sees or writes the internal note. */
    private boolean seesInternalNote(ErgPrincipal principal, String userId) {
        return !userId.equals(principal.userId()) && canManage(principal);
    }

    private String currentUserId() {
        return SecurityUtils.requirePrincipal().userId();
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
