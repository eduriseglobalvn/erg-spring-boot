package com.erg.ergspringboot.curriculum.internal;

import com.erg.ergspringboot.platform.security.MethodAuthorizationEvaluator;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * B4: content lifecycle writes {@code in_review}/{@code approved} into curriculum node status. Curriculum
 * managers must still see those nodes in the tree and may build on them; everyone else keeps the
 * active-only view.
 */
@Component
public class CurriculumReviewAccess {
    public static final Set<String> REVIEW_STATES = Set.of("in_review", "approved");
    private final MethodAuthorizationEvaluator evaluator;

    public CurriculumReviewAccess(MethodAuthorizationEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /** Fail-closed instance for repository-built compatibility adapters. */
    public static CurriculumReviewAccess denyAll() {
        return new CurriculumReviewAccess(null);
    }

    public boolean canSeeReviewStates() {
        return evaluator != null && evaluator.isAllowedAny(
                SecurityContextHolder.getContext().getAuthentication(), "curriculum.manage", "*");
    }

    public static boolean isReviewState(String status) {
        return status != null && REVIEW_STATES.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    /** Active nodes are always usable parents; in-review/approved ones only for curriculum managers. */
    public boolean isUsableParent(String status) {
        if (status != null && "active".equalsIgnoreCase(status.trim())) return true;
        return isReviewState(status) && canSeeReviewStates();
    }
}
