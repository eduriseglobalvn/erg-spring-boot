package com.erg.ergspringboot.content.lifecycle.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

/** Module-owned authorization policy for lifecycle commands. */
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'content.manage', 'curriculum.manage', 'content.lifecycle.submit', 'content.lifecycle.approve', 'content.lifecycle.publish', 'content.lifecycle.archive', 'content.lifecycle.rollback', 'lms.quiz.publish')")
public @interface ContentLifecycleTransition {
}
