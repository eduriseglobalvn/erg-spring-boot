package com.erg.ergspringboot.users;

import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.users.internal.authorization.LegacyUserAuthorizationAdapter;

/** Deprecated construction seam for non-Spring legacy authorization callers. */
@Deprecated(since = "2026-08-08", forRemoval = false)
public final class UserAuthorizationCompatibility {
    private UserAuthorizationCompatibility() { }

    public static UserAuthorizationPort fromRepositories(
            UserRepository users, UserPermissionOverrideRepository overrides) {
        return new LegacyUserAuthorizationAdapter(users, overrides);
    }
}
