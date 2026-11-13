package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import com.erg.ergspringboot.users.persistence.User;
import java.util.Optional;

/**
 * The Argon2 verdict for one login, computed before the login transaction opens so that hashing
 * (~50 ms of CPU and 16 MB each) never holds a pooled DB connection. Exactly one hash runs per
 * attempt, against the stored hash or a dummy, so timing reveals neither account existence nor lockout.
 */
record CheckedPassword(String verifiedHash, boolean matched) {

    static CheckedPassword check(PasswordService passwords, Optional<User> found, String rawPassword) {
        String hash = found.map(User::getPasswordHash).orElse(null);
        if (hash == null || hash.isBlank()) {
            passwords.burnUnknownAccountCheck(rawPassword);
            return new CheckedPassword(null, false);
        }
        return new CheckedPassword(hash, passwords.matches(rawPassword, hash));
    }

    /** True only for the hash that was verified: a password changed in between counts as a mismatch. */
    boolean matches(String storedHash) {
        return matched && verifiedHash != null && verifiedHash.equals(storedHash);
    }
}
