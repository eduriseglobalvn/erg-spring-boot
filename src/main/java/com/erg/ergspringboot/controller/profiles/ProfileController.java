package com.erg.ergspringboot.controller.profiles;

import com.erg.ergspringboot.common.request.profiles.ProfileRequests.CreateProfileRequest;
import com.erg.ergspringboot.common.request.profiles.ProfileRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.profiles.ProfileResponses.ProfileResponse;
import com.erg.ergspringboot.exception.UnauthorizedException;
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

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{userId}")
    public ApiResponse<ProfileResponse> getProfile(@PathVariable String userId, HttpServletRequest request) {
        return ok("Profile loaded", profileService.getProfile(userId), request);
    }

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getCurrentProfile(HttpServletRequest request) {
        return ok("Profile loaded", profileService.getCurrentProfile(userId(request)), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProfileResponse> createProfile(@Valid @RequestBody CreateProfileRequest body, HttpServletRequest request) {
        return ApiResponse.success(201, "Profile created", profileService.createProfile(body), request.getRequestURI(), requestId(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<ProfileResponse> updateProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateProfileRequest body,
            HttpServletRequest request
    ) {
        return ok("Profile updated", profileService.updateProfile(userId, body), request);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteProfile(@PathVariable String userId, HttpServletRequest request) {
        profileService.deleteProfile(userId);
        return ok("Profile deleted", null, request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String userId(HttpServletRequest request) {
        String value = request.getHeader("X-User-ID");
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException("missing authenticated user");
        }
        return value.trim();
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
