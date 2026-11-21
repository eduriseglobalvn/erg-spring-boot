package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attempt time rules: the assignment open window, the deadline frozen in the start policy snapshot (falling back
 * to the live assignment {@code dueAt}/duration for legacy attempts) and the server-side submit grace.
 */
final class AttemptDeadlinePolicy {
    private static final Logger log = LoggerFactory.getLogger(AttemptDeadlinePolicy.class);
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

    /**
     * The later of the start-snapshot deadline and the live assignment deadline (a teacher extending dueAt lifts running
     * attempts, shortening never cuts them), capped by the attempt's own snapshot duration. Legacy attempts use the live one.
     */
    OffsetDateTime effectiveDeadline(Attempt attempt, Assignment assignment) {
        OffsetDateTime snapshot = snapshotDeadline(attempt);
        OffsetDateTime live = deadline(assignment, attempt.getStartedAt());
        if (snapshot == null) return live;
        OffsetDateTime result = live != null && live.isAfter(snapshot) ? live : snapshot;
        Integer minutes = snapshotInteger(attempt, "durationMinutes");
        if (minutes == null || minutes <= 0 || attempt.getStartedAt() == null) return result;
        OffsetDateTime cap = attempt.getStartedAt().plusMinutes(minutes);
        return result.isAfter(cap) ? cap : result;
    }

    boolean isExpired(Attempt attempt, Assignment assignment, OffsetDateTime now) {
        OffsetDateTime deadline = effectiveDeadline(attempt, assignment);
        return deadline != null && now.isAfter(deadline.plus(submitGrace));
    }

    void ensureOpen(Assignment a, OffsetDateTime now) { if (a.getStartAt() != null && now.isBefore(a.getStartAt())) throw new BadRequestException("assignment has not started"); if (a.getDueAt() != null && now.isAfter(a.getDueAt())) throw new BadRequestException("assignment is past due"); }

    boolean isPastDue(Assignment a, OffsetDateTime now) { return a.getDueAt() != null && now.isAfter(a.getDueAt()); }

    /** A submission after the deadline is still stored (never dropped); it is just flagged late. */
    boolean isLate(Attempt attempt, Assignment assignment, OffsetDateTime submitted) {
        OffsetDateTime deadline = effectiveDeadline(attempt, assignment);
        return deadline != null && submitted.isAfter(deadline);
    }

    private OffsetDateTime snapshotDeadline(Attempt attempt) {
        Object value = map(attempt.getPolicySnapshot()).get("deadlineAt");
        if (value == null || value.toString().isBlank()) return null;
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException invalid) {
            // P3-6: a corrupt snapshot is a server bug; make it visible and keep the bounded live deadline.
            log.warn("Attempt {} has an unparseable policy snapshot deadlineAt; using the assignment deadline", attempt.getId());
            return null;
        }
    }

    private Integer snapshotInteger(Attempt attempt, String key) {
        Object value = map(attempt.getPolicySnapshot()).get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException invalid) {
            log.warn("Attempt {} has an unparseable policy snapshot {}; ignoring it", attempt.getId(), key);
            return null;
        }
    }

    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
