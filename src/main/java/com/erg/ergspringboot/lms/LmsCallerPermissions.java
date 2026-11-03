package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.platform.security.MethodAuthorizationEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Evaluates the authenticated caller's permissions with the same deny-aware policy as method security. */
@Component
public class LmsCallerPermissions {
    private final MethodAuthorizationEvaluator evaluator;

    public LmsCallerPermissions(MethodAuthorizationEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public boolean hasAny(String... permissions) {
        return evaluator.isAllowedAny(SecurityContextHolder.getContext().getAuthentication(), permissions);
    }
}
