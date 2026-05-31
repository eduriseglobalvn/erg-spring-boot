package com.erg.ergspringboot.common.request.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank @Size(min = 2, max = 100) String fullName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            String deviceId,
            String deviceName,
            String deviceFingerprint,
            String portal,
            Boolean rememberMe
    ) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken,
            String deviceId
    ) {
    }

    public record GoogleLoginRequest(
            String idToken,
            @NotBlank @Email String email,
            String fullName,
            String avatarUrl,
            @NotBlank String googleSub,
            Boolean emailVerified,
            String deviceId,
            String deviceName,
            String deviceFingerprint,
            String portal
    ) {
    }

    public record SocialLoginRequest(
            String idToken,
            @NotBlank @Email String email,
            String fullName,
            String avatarUrl,
            @NotBlank String providerId,
            Boolean emailVerified,
            String deviceId,
            String deviceName,
            String deviceFingerprint,
            String portal,
            String accessToken,
            String refreshToken
    ) {
    }

    public record VerifyPinRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 6) String code,
            @NotBlank String purpose
    ) {
    }

    public record ResendPinRequest(
            @NotBlank @Email String email,
            String purpose
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 6) String code,
            @JsonProperty("new_password")
            @NotBlank @Size(min = 8) String newPassword
    ) {
    }
}
