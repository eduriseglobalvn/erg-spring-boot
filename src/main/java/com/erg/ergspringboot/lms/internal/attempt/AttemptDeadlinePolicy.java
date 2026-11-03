package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.Attempt;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Attempt time rules: the assignment open window, the deadline frozen in the start policy snapshot (falling back
 * to the live assignment {@code dueAt}/duration for legacy attempts) and the server-side submit grace.
 */
final class AttemptDeadlinePolicy {
    private final Duration submitGrace;

    AttemptDeadlinePolicy(Duration submitGrace) {
        this.submitGrace = submitGrace == null ? Duration.ZERO : submitGrace;
    }

    Duration submitGrace() {
        return submitGrace;
    }

    /** The earlier of the assignment due date and {@code startedAt + maxDurationMinutes}. */
    OffsetDateTime deadline(Assignment assignment, OffsetDateTime startedAt) {
        OffsetDateTime result = assignment.getDueAt();
        Integer minutes = assignment.getMaxDurationMinutes();
        if (minutes != null && minutes > 0 && startedAt != null) {
            OffsetDateTime durationDeadline = startedAt.plusMinutes(minutes);
            result = result == null || durationDeadline.isBefore(result) ? durationDeadline : result;
        }
        return result;
    }

    /** Deadline frozen in the start snapshot; legacy attempts without one fall back to the live assignment. */
    OffsetDateTime effectiveDeadline(Attempt attempt, Assignment assignment) {
        OffsetDateTime snapshot = snapshotDeadline(attempt);
        return snapshot != null ? snapshot : deadline(assignment, attempt.getStartedAt());
    }

    boolean isExpired(Attempt attempt, Assignment assignment, OffsetDateTime now) {
        OffsetDateTime deadline = effectiveDeadline(attempt, assignment);
        return deadline != null && now.isAfter(deadline.plus(submitGrace));
    }

    void ensureOpen(Assignment a, OffsetDateTime now) { if (a.getStartAt() != null && now.isBefore(a.getStartAt())) throw new BadRequestException("assignment has not started"); if (a.getDueAt() != null && now.isAfter(a.getDueAt())) throw new BadRequestException("assignment is past due"); }

    /** Accepts submissions up to {@code deadline + grace}; returns true when the submission is late. */
    boolean ensureSubmitAllowed(Attempt attempt, Assignment assignment, OffsetDateTime submitted) {
        OffsetDateTime deadline = effectiveDeadline(attempt, assignment);
        if (deadline == null || !submitted.isAfter(deadline)) return false;
        if (!submitted.isAfter(deadline.plus(submitGrace))) return true;
        Integer minutes = snapshotDeadline(attempt) != null ? snapshotInteger(attempt, "durationMinutes") : assignment.getMaxDurationMinutes();
        if (minutes != null && minutes > 0 && attempt.getStartedAt() != null
                && submitted.isAfter(attempt.getStartedAt().plusMinutes(minutes).plus(submitGrace))) {
            throw new BadRequestException("attempt duration exceeded");
        }
        throw new BadRequestException("assignment is past due");
    }

    private OffsetDateTime snapshotDeadline(Attempt attempt) {
        Object value = map(attempt.getPolicySnapshot()).get("deadlineAt");
        if (value == null || value.toString().isBlank()) return null;
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer snapshotInteger(Attempt attempt, String key) {
        Object value = map(attempt.getPolicySnapshot()).get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null || value.toString().isBlank()) return null;
        try { return Integer.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }

    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
