package com.erg.ergspringboot.content.lifecycle;

import com.erg.ergspringboot.exception.BadRequestException;

/** Single source of truth for content lifecycle transitions. */
public final class LifecycleTransitionPolicy {
    private LifecycleTransitionPolicy() { }

    public static LifecycleTransition validate(LifecycleState from, LifecycleState to, String reason) {
        return validate(from, to, reason, false);
    }

    /**
     * The quiz compiler already performs validation and persists an immutable version in the
     * same transaction. This is the narrow internal bridge for that operation; it does not make
     * the public endpoint a draft-to-published shortcut.
     */
    public static LifecycleTransition validateValidatedQuizPublish(LifecycleState from, String reason) {
        if (from == null) throw new BadRequestException("lifecycle state is required");
        if (from == LifecycleState.PUBLISHED) {
            throw new BadRequestException("lifecycle state is already published");
        }
        if (from == LifecycleState.ARCHIVED) {
            throw new BadRequestException("archived quiz must be restored before publishing");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("reason is required for quiz publish");
        }
        return new LifecycleTransition(from, LifecycleState.PUBLISHED, false);
    }

    /** Quiz rollback forks an immutable published snapshot; other resources restore from ARCHIVED. */
    public static LifecycleTransition validate(
            LifecycleState from, LifecycleState to, String reason, boolean quizRollback) {
        if (from == null || to == null) throw new BadRequestException("lifecycle state is required");
        if (from == to) throw new BadRequestException("lifecycle state is already " + to.name().toLowerCase());
        boolean allowed = switch (from) {
            case DRAFT -> to == LifecycleState.IN_REVIEW || to == LifecycleState.ARCHIVED;
            case IN_REVIEW -> to == LifecycleState.APPROVED
                    || to == LifecycleState.DRAFT
                    || to == LifecycleState.ARCHIVED;
            case APPROVED -> to == LifecycleState.PUBLISHED
                    || to == LifecycleState.DRAFT
                    || to == LifecycleState.ARCHIVED;
            case PUBLISHED -> to == LifecycleState.ARCHIVED
                    || (quizRollback && to == LifecycleState.DRAFT);
            case ARCHIVED -> to == LifecycleState.DRAFT;
        };
        if (!allowed) {
            throw new BadRequestException("illegal lifecycle transition " + from + " -> " + to);
        }
        if (to == LifecycleState.ARCHIVED
                || (from == LifecycleState.PUBLISHED && to == LifecycleState.DRAFT)
                || (from == LifecycleState.IN_REVIEW && to == LifecycleState.DRAFT)
                || (quizRollback && to == LifecycleState.DRAFT)) {
            if (reason == null || reason.isBlank()) throw new BadRequestException("reason is required for this lifecycle transition");
        }
        return new LifecycleTransition(from, to, quizRollback && to == LifecycleState.DRAFT);
    }
}
