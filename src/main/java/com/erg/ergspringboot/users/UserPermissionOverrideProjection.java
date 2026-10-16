package com.erg.ergspringboot.users;

import java.time.OffsetDateTime;

/** Persistence-neutral view of a user permission override. */
public record UserPermissionOverrideProjection(
        String id,
        String userId,
        String permission,
        String grantType,
        String reason,
        OffsetDateTime expiresAt,
        String createdBy,
        OffsetDateTime createdAt) {
}
