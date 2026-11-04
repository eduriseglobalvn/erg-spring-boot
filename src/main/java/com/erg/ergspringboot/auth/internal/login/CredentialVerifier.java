package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import java.util.Objects;
import java.util.Optional;

/**
 * Verifies a login's credentials and account status in a fixed, disclosure-safe order:
 * unknown account, temporary lockout, password, then account status. Every rejection is
 * recorded through {@link LoginAttemptRecorder} before the {@link AuthFailure} is thrown.
 * Runs inside the caller's transaction; it is not a Spring bean and adds no proxy boundary.
 */
final class CredentialVerifier {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_BANNED = "BANNED";
    private static final String PROVIDER_LOCAL = "local";

    private final PasswordService passwords;
    private final LoginAttemptRecorder attemptRecorder;
    private LoginFailureEnforcementService failureEnforcement;

    CredentialVerifier(
            PasswordService passwords,
            LoginFailureEnforcementService failureEnforcement,
            LoginAttemptRecorder attemptRecorder) {
        this.passwords = passwords;
        this.failureEnforcement = Objects.requireNonNull(failureEnforcement, "failureEnforcement");
        this.attemptRecorder = attemptRecorder;
    }

    void setLoginFailureEnforcementService(LoginFailureEnforcementService failureEnforcement) {
        this.failureEnforcement = Objects.requireNonNull(failureEnforcement, "failureEnforcement");
    }

    /**
     * Returns the verified user, or throws the {@link AuthFailure} the caller must surface.
     *
     * @param found the account resolved for {@code identifier}, if any
     */
    VerifiedLogin verify(
            Optional<User> found, String identifier, String password,
            AuthClientContext context, String portal) {
        User user = found.orElseThrow(() -> {
            passwords.burnUnknownAccountCheck(password);
            attemptRecorder.recordFailure(null, identifier, context, portal, "invalid_credentials", false);
            return new AuthFailure.InvalidCredentials();
        });
        String email = user.getEmail();

        // A locked-out account answers exactly like a wrong password and never evaluates the
        // submitted password, so lockout reveals neither account existence nor password validity.
        if (failureEnforcement.isTemporarilyLocked(user)) {
            passwords.burnUnknownAccountCheck(password);
            attemptRecorder.recordFailure(user, email, context, portal, "account_temporarily_locked", false);
            throw new AuthFailure.InvalidCredentials();
        }
        // Wrong passwords always yield AUTH_INVALID_CREDENTIALS; account status is disclosed
        // only to a caller that proved knowledge of the password.
        if (!passwords.matches(password, user.getPasswordHash())) {
            attemptRecorder.recordFailure(user, email, context, portal, "invalid_credentials", true);
            throw new AuthFailure.InvalidCredentials();
        }
        if (isLocked(user)) {
            attemptRecorder.recordFailure(user, email, context, portal, "account_locked", false);
            throw new AuthFailure.AccountLocked();
        }
        if ("INACTIVE".equalsIgnoreCase(user.getStatus())) {
            attemptRecorder.recordFailure(user, email, context, portal, "account_disabled", false);
            throw new AuthFailure.AccountDisabled();
        }
        boolean restrictedFirstLogin = "INVITED".equalsIgnoreCase(user.getStatus())
                && Boolean.TRUE.equals(user.getMustChangePassword());
        if ("INVITED".equalsIgnoreCase(user.getStatus()) && !restrictedFirstLogin) {
            attemptRecorder.recordFailure(user, email, context, portal, "account_activation_required", false);
            throw new AuthFailure.AccountActivationRequired();
        }
        if (STATUS_PENDING.equalsIgnoreCase(user.getStatus())
                && PROVIDER_LOCAL.equalsIgnoreCase(user.getProvider())) {
            attemptRecorder.recordFailure(user, email, context, portal, "email_not_verified", false);
            throw new AuthFailure.EmailNotVerified();
        }
        return new VerifiedLogin(user, restrictedFirstLogin);
    }

    private boolean isLocked(User user) {
        return STATUS_BLOCKED.equalsIgnoreCase(user.getStatus())
                || STATUS_BANNED.equalsIgnoreCase(user.getStatus());
    }

    /**
     * @param restrictedFirstLogin an invited account that must change its password; the caller
     *        activates it before issuing the session
     */
    record VerifiedLogin(User user, boolean restrictedFirstLogin) {
    }
}
