package com.erg.ergspringboot.users;

import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.users.internal.directory.LegacyUserDirectoryAdapter;

/** Deprecated construction seam for non-Spring legacy callers. */
@Deprecated(since = "2026-08-08", forRemoval = false)
public final class UserDirectoryCompatibility {
    private UserDirectoryCompatibility() { }

    public static UserDirectoryPort fromRepository(UserRepository users) {
        return new LegacyUserDirectoryAdapter(users);
    }
}
