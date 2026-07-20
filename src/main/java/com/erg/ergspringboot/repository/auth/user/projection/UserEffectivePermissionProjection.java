package com.erg.ergspringboot.repository.auth.user.projection;

public record UserEffectivePermissionProjection(
        String userId,
        String permission,
        String source
) {
}
