package com.erg.ergspringboot.users.persistence;

public record UserEffectivePermissionProjection(
        String userId,
        String permission,
        String source
) {
}
