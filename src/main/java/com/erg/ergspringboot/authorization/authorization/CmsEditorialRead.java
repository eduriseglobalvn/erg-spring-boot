package com.erg.ergspringboot.authorization.authorization;

import java.lang.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** Explicit policy for non-public CMS editorial lists. */
@Documented @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'cms.posts.update', 'cms.posts.delete', '*')")
public @interface CmsEditorialRead { }
