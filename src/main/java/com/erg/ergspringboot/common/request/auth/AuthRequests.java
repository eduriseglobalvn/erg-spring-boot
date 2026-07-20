package com.erg.ergspringboot.common.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String identifier,
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

}
