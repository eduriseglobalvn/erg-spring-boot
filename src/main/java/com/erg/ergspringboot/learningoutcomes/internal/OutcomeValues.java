package com.erg.ergspringboot.learningoutcomes.internal;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.learningoutcomes.Competency;
import com.erg.ergspringboot.model.learningoutcomes.CompetencyAttainment;
import com.erg.ergspringboot.model.learningoutcomes.LearningPath;
import com.erg.ergspringboot.model.learningoutcomes.LearningPathCompletion;
import com.erg.ergspringboot.model.learningoutcomes.OutcomeCertificate;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Shared statuses, input normalization and payload hashing for the learning-outcome use cases. */
final class OutcomeValues {
    static final String PUBLISHED = "PUBLISHED";
    static final String ISSUED = "ISSUED";
    static final String COMPLETED = "COMPLETED";
    static final int MAX_LIST = 100;

    private OutcomeValues() {
    }

    static long version(Object entity) {
        return switch (entity) {
            case LearningPath value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            case Competency value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            case CompetencyAttainment value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            case LearningPathCompletion value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            case OutcomeCertificate value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            default -> 0;
        };
    }

    static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    static String scope(UserAccessContext access) {
        return access.currentClassId() == null ? access.currentEducationUnitId() : access.currentClassId();
    }

    static String key(String value) {
        return required(value, "idempotencyKey", 255);
    }

    static String required(String value, String name, int max) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new BadRequestException(name + " is required");
        if (normalized.length() > max) throw new BadRequestException(name + " is too long");
        return normalized;
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String resolveEventCorrelation(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    static String hash(String operation, String... values) {
        StringBuilder payload = new StringBuilder(operation);
        for (String value : values) payload.append('\u001f').append(value == null ? "" : value);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
