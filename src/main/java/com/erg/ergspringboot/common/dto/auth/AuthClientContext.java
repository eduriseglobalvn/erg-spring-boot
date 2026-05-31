package com.erg.ergspringboot.common.dto.auth;

public record AuthClientContext(
        String ipAddress,
        String userAgent,
        String deviceId,
        String deviceName,
        String deviceFingerprint
) {
}
