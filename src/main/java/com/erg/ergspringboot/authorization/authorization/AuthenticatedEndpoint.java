package com.erg.ergspringboot.authorization.authorization;

import java.lang.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** Explicit authentication boundary for operations without a module permission yet. */
@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAuthenticated()")
public @interface AuthenticatedEndpoint { }
