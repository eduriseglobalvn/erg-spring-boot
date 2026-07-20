package com.erg.ergspringboot.repository.organization.projection;

import java.time.OffsetDateTime;

public record UserAccessScopeProjection(
        String id,
        String userId,
        String educationUnitId,
        String modules,
        String role,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
