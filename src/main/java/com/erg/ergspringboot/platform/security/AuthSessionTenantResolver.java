package com.erg.ergspringboot.platform.security;

import java.util.Optional;

/** Resolves a bearer session tenant from server-owned authentication state. */
public interface AuthSessionTenantResolver {

    Optional<String> resolve(String accessTokenHash);
}
