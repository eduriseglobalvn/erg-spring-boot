package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.exception.BadRequestException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Module-owned state vocabulary and fail-closed filter normalization. */
public final class LmsStatePolicy {

    private static final Set<String> ASSIGNMENT_FILTERS = Set.of(
            "draft", "assigned", "active", "published", "archived", "late", "overdue");
    private static final Set<String> ATTEMPT_FILTERS = Set.of(
            "in_progress", "started", "submitted", "needs_review", "review_required", "graded", "completed");
    private static final Set<String> TERMINAL_ATTEMPT_STATES = Set.of("submitted", "graded", "completed");
    private static final Set<String> REVIEW_ATTEMPT_STATES = Set.of("needs_review", "review_required");
    /**
     * Single source of truth for "the learner has handed the attempt in". {@code needs_review} is included on
     * purpose: it counts against attempt limits AND marks the assignment as done on dashboards/progress.
     */
    private static final List<String> COMPLETED_ATTEMPT_STATES = List.of(
            "submitted", "needs_review", "review_required", "graded", "completed");
    /** Attempts a teacher still has to look at (submitted without a final grade, or awaiting manual grading). */
    private static final List<String> REVIEW_QUEUE_ATTEMPT_STATES = List.of(
            "submitted", "needs_review", "review_required");
    /** Persisted assignment states; {@code late}/{@code overdue} are derived filters, never stored. */
    private static final Set<String> STORED_ASSIGNMENT_STATES = Set.of(
            "draft", "assigned", "active", "published", "archived");

    private LmsStatePolicy() { }

    /** Validates a client-supplied assignment status before it is persisted. */
    public static String assignmentState(String raw) {
        String normalized = normalize(raw, STORED_ASSIGNMENT_STATES, "assignment");
        if (normalized == null) throw new BadRequestException("unsupported assignment status: " + raw);
        return normalized;
    }

    public static List<String> reviewQueueAttemptStates() {
        return REVIEW_QUEUE_ATTEMPT_STATES;
    }

    public static boolean attemptIsCompleted(String raw) {
        return COMPLETED_ATTEMPT_STATES.contains(normalizeState(raw));
    }

    public static String gradedAttemptState() {
        return "graded";
    }

    public static String assignmentFilter(String raw) {
        return normalize(raw, ASSIGNMENT_FILTERS, "assignment");
    }

    public static String attemptFilter(String raw) {
        return normalize(raw, ATTEMPT_FILTERS, "attempt");
    }

    public static boolean attemptIsTerminal(String raw) {
        String state = normalizeState(raw);
        return TERMINAL_ATTEMPT_STATES.contains(state);
    }

    public static boolean attemptNeedsReview(String raw) {
        String state = normalizeState(raw);
        return REVIEW_ATTEMPT_STATES.contains(state);
    }

    /** A submitted attempt, including one awaiting manual review, cannot be edited by the learner. */
    public static boolean attemptIsEditable(String raw) {
        String state = normalizeState(raw);
        return !TERMINAL_ATTEMPT_STATES.contains(state) && !REVIEW_ATTEMPT_STATES.contains(state);
    }

    /** Persisted states counted against an assignment attempt limit. */
    public static List<String> completedAttemptStates() {
        return COMPLETED_ATTEMPT_STATES;
    }

    public static String initialAttemptState() {
        return "in_progress";
    }

    public static String submittedAttemptState() {
        return "submitted";
    }

    public static String manualReviewAttemptState() {
        return "needs_review";
    }

    public static boolean assignmentIsVisible(String raw) {
        String state = normalizeState(raw);
        return Set.of("assigned", "active", "published").contains(state);
    }

    public static String normalizeState(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "over_due".equals(normalized) ? "overdue" : normalized;
    }

    private static String normalize(String raw, Set<String> allowed, String aggregate) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = normalizeState(raw);
        if (!allowed.contains(normalized)) {
            throw new BadRequestException("unsupported " + aggregate + " status: " + raw);
        }
        return normalized;
    }
}
