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

    public static final class InvalidToken extends AuthFailure {
        public InvalidToken() {
            super(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "invalid or expired refresh token");
        }
    }

    public static final class InvalidPin extends AuthFailure {
        public InvalidPin() {
            super(HttpStatus.BAD_REQUEST, "INVALID_PIN", "invalid or expired PIN");
        }
    }

    public static final class GoogleIdentityInvalid extends AuthFailure {
        public GoogleIdentityInvalid() {
            super(HttpStatus.UNAUTHORIZED, "GOOGLE_IDENTITY_INVALID", "invalid Google identity");
        }
    }

    public static final class SocialIdentityInvalid extends AuthFailure {
        public SocialIdentityInvalid() {
            super(HttpStatus.UNAUTHORIZED, "SOCIAL_IDENTITY_INVALID", "invalid social identity");
        }
    }

    public static final class OAuthNotConfigured extends AuthFailure {
        public OAuthNotConfigured() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_NOT_CONFIGURED", "OAuth provider is not configured");
        }
    }
}
