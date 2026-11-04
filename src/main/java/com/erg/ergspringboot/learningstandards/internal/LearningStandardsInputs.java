package com.erg.ergspringboot.learningstandards.internal;

import com.erg.ergspringboot.exception.BadRequestException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.regex.Pattern;

/** Input validation and normalization for learning-standards commands. */
final class LearningStandardsInputs {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private LearningStandardsInputs() {
    }

    static String required(String value, String name, int max) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new BadRequestException(name + " is required");
        if (normalized.length() > max) throw new BadRequestException(name + " is too long");
        return normalized;
    }

    static String key(String value) {
        return required(value, "idempotencyKey", 255);
    }

    static String sha(String value, String field) {
        if (!SHA256.matcher(value).matches()) throw new BadRequestException(field + " must be a SHA-256 hex digest");
        return value.toLowerCase(Locale.ROOT);
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static OffsetDateTime parseTime(String value) {
        if (trimToNull(value) == null) return now();
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(value.trim());
            if (parsed.isAfter(now().plusMinutes(5))) throw new BadRequestException("occurredAt is in the future");
            return parsed;
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BadRequestException("occurredAt must be ISO-8601");
        }
    }

    static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
