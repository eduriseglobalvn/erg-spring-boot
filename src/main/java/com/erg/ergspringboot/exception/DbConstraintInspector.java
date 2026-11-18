package com.erg.ergspringboot.exception;

import java.util.regex.Pattern;

/** Reads a DataIntegrityViolationException cause chain; kept out of {@link GlobalExceptionHandler} to hold its size limit. */
final class DbConstraintInspector {
    private static final Pattern IDEMPOTENCY_CONSTRAINT =
            Pattern.compile("constraint \"?[A-Za-z0-9_]*idempotency[A-Za-z0-9_]*\"?", Pattern.CASE_INSENSITIVE);

    private DbConstraintInspector() {
    }

    static boolean isForeignKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && "23503".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    static boolean mentionsIdempotencyConstraint(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            // The constraint's own name only: the failed INSERT text also lists an idempotency_key column, which is not a retry.
            if (message != null && IDEMPOTENCY_CONSTRAINT.matcher(message).find()) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
