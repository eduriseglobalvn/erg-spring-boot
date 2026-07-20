package com.erg.ergspringboot.repository.auth.user.projection;

import java.time.OffsetDateTime;

public record UserAccessRowProjection(
        String userId,
        String fullName,
        String email,
        String status,
        Long roleCount,
        Long scopeCount,
        OffsetDateTime createdAt
) {
}
