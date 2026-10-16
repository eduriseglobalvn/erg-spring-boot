package com.erg.ergspringboot.authorization.authorization;

import java.lang.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'account.self.read', 'profile.self', '*')")
public @interface UserSelfRead { }
