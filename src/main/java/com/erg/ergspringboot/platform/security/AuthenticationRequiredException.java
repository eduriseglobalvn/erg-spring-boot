package com.erg.ergspringboot.platform.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/** Raised when application code attempts to construct a user context without verified authentication. */
public final class AuthenticationRequiredException extends AuthenticationCredentialsNotFoundException {

    public AuthenticationRequiredException() {
        super("A verified principal is required to construct a user access context");
    }
}
