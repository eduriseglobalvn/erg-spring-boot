package com.erg.ergspringboot.exception.auth;

import com.erg.ergspringboot.exception.ApiException;
import org.springframework.http.HttpStatus;

public abstract class AuthFailure extends ApiException {

    protected AuthFailure(HttpStatus status, String code, String message) {
        super(status, code, "Authentication Error", message);
    }

    public static final class InvalidCredentials extends AuthFailure {
        public InvalidCredentials() {
            super(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "invalid email or password");
        }
    }

    public static final class EmailExists extends AuthFailure {
        public EmailExists() {
            super(HttpStatus.CONFLICT, "EMAIL_EXISTS", "email already registered");
        }
    }

    public static final class EmailNotVerified extends AuthFailure {
        public EmailNotVerified() {
            super(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "email not verified");
        }
    }

    public static final class TooManyAttempts extends AuthFailure {
        public TooManyAttempts() {
            super(HttpStatus.TOO_MANY_REQUESTS, "AUTH_TOO_MANY_ATTEMPTS", "too many login attempts, please try again later");
        }
    }

    public static final class IpBlocked extends AuthFailure {
        public IpBlocked() {
            super(HttpStatus.FORBIDDEN, "AUTH_IP_BLOCKED", "login from this IP is blocked");
        }
    }

    public static final class GeoBlocked extends AuthFailure {
        public GeoBlocked() {
            super(HttpStatus.FORBIDDEN, "AUTH_GEO_BLOCKED", "login from this region is not allowed");
        }
    }

    public static final class AccountLocked extends AuthFailure {
        public AccountLocked() {
            super(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "account temporarily locked");
        }
    }

    public static final class AccountDisabled extends AuthFailure {
        public AccountDisabled() {
            super(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "account is inactive");
        }
    }

    public static final class LoginInProgress extends AuthFailure {
        public LoginInProgress() {
            super(HttpStatus.CONFLICT, "AUTH_LOGIN_IN_PROGRESS", "another login for this account is still being processed");
        }
    }

    public static final class DeviceLimitExceeded extends AuthFailure {
        public DeviceLimitExceeded() {
            super(HttpStatus.CONFLICT, "DEVICE_LIMIT_EXCEEDED", "device limit exceeded; existing sessions were revoked");
        }
    }

    public static final class PortalForbidden extends AuthFailure {
        public PortalForbidden() {
            super(HttpStatus.FORBIDDEN, "PORTAL_FORBIDDEN", "account is not entitled to this portal");
        }
    }

    public static final class AccountActivationRequired extends AuthFailure {
        public AccountActivationRequired() {
            super(HttpStatus.FORBIDDEN, "ACCOUNT_ACTIVATION_REQUIRED", "account activation and required identity actions are incomplete");
        }
    }

    public static final class InvalidToken extends AuthFailure {
        public InvalidToken() {
            super(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "invalid or expired refresh token");
        }
    }

}
