package com.erg.ergspringboot.users.persistence;

import java.time.OffsetDateTime;

public record UserListItemProjection(
        String id,
        String email,
        String fullName,
        String avatarUrl,
        String phone,
        String status,
        String provider,
        String accountType,
        String lastLoginProvider,
        Boolean isProfileCompleted,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt
) {
}
