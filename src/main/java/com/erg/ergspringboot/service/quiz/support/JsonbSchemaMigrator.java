package com.erg.ergspringboot.service.quiz.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Tolerant reader for versioned JSONB blobs (spec §11.1).
 *
 * <p>Every quiz-domain JSONB blob (draft tree/settings, runtime_quiz, answer_key, package_json,
 * attempt answers, bank content) carries a top-level {@code schemaVersion}. A blob written under an
 * older schema is upgraded in memory to the current shape by replaying an ordered chain of
 * migration steps — no DB migration is needed for additive/rename/drop-field changes.
 *
 * <p>Immutable published blobs ({@code quiz_versions}, {@code quiz_packages}) are never rewritten,
 * so the migrator must keep every step ever released (cumulative) to stay able to read old data.
 */
public final class JsonbSchemaMigrator {

    public static final String SCHEMA_VERSION_KEY = "schemaVersion";

    /** A single upgrade step: transforms a blob from {@link #fromVersion()} to {@code fromVersion + 1}. */
    public interface SchemaMigration {
        int fromVersion();

        Map<String, Object> apply(Map<String, Object> payload);
    }

    public enum ReadStatus {
        ABSENT("absent"),
        LEGACY_DEFAULTED("legacy_defaulted"),
        MIGRATED("migrated"),
        CURRENT("current"),
        UNSUPPORTED_FUTURE("unsupported_future"),
        INVALID("invalid");

        private final String metricSuffix;

        ReadStatus(String metricSuffix) {
            this.metricSuffix = metricSuffix;
        }
    }

    /** Result of a tolerant read. A future payload is intentionally unavailable, never downgraded. */
    public record MigrationReadResult(
            Map<String, Object> payload,
            ReadStatus status,
            int sourceVersion,
            int targetVersion,
            String metricLabel
    ) {
        public boolean usable() {
            return switch (status) {
                case UNSUPPORTED_FUTURE, INVALID -> false;
                default -> true;
            };
        }
    }

    private static final class UnsupportedFutureVersionException extends IllegalStateException {
        private UnsupportedFutureVersionException(String message) {
            super(message);
        }
    }

    private final String blobType;
    private final int targetVersion;
    private final Map<Integer, SchemaMigration> stepsByFromVersion;

    private JsonbSchemaMigrator(String blobType, int targetVersion, Map<Integer, SchemaMigration> steps) {
        this.blobType = blobType;
        this.targetVersion = targetVersion;
        this.stepsByFromVersion = steps;
    }

    public static Builder forType(String blobType) {
        return new Builder(blobType);
    }

    /**
     * Reads {@code schemaVersion} from the payload and replays every migration step up to the target
     * version, returning a new map stamped with the target version. A missing/blank version is
     * treated as version 1 (the earliest released shape). Returns {@code null} for a {@code null}
     * input so callers can pass through absent blobs.
     */
    public Map<String, Object> migrateToLatest(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        int version = readVersion(payload);
        if (version > targetVersion) {
            throw new UnsupportedFutureVersionException(
                    "Blob '" + blobType + "' has schemaVersion " + version
                            + " which is newer than supported target " + targetVersion
                            + " — upgrade the service before reading this data");
        }
        Map<String, Object> current = new LinkedHashMap<>(payload);
        while (version < targetVersion) {
            SchemaMigration step = stepsByFromVersion.get(version);
            if (step == null) {
                throw new IllegalStateException(
                        "Blob '" + blobType + "' missing migration step from schemaVersion " + version
                                + " to " + (version + 1));
            }
            current = new LinkedHashMap<>(step.apply(current));
            version++;
        }
        current.put(SCHEMA_VERSION_KEY, targetVersion);
        return current;
    }

    /**
     * Reads a durable payload without allowing a future writer to be interpreted as the current
     * shape. The strict {@link #migrateToLatest(Map)} method remains available for command-time
     * validation; read paths should use this method and expose {@link MigrationReadResult#metricLabel()}
     * to their bounded metrics.
     */
    public MigrationReadResult readSafely(Map<String, Object> payload) {
        if (payload == null) {
            return result(null, ReadStatus.ABSENT, 0);
        }
        int sourceVersion;
        try {
            sourceVersion = readVersion(payload);
        } catch (IllegalStateException ex) {
            return result(null, ReadStatus.INVALID, -1);
        }
        try {
            Map<String, Object> migrated = migrateToLatest(payload);
            boolean explicit = payload.containsKey(SCHEMA_VERSION_KEY)
                    && payload.get(SCHEMA_VERSION_KEY) != null
                    && !payload.get(SCHEMA_VERSION_KEY).toString().trim().isEmpty();
            ReadStatus status = !explicit
                    ? ReadStatus.LEGACY_DEFAULTED
                    : sourceVersion == targetVersion ? ReadStatus.CURRENT : ReadStatus.MIGRATED;
            return result(migrated, status, sourceVersion);
        } catch (UnsupportedFutureVersionException ex) {
            return result(null, ReadStatus.UNSUPPORTED_FUTURE, sourceVersion);
        }
    }

    public String blobType() {
        return blobType;
    }

    public int targetVersion() {
        return targetVersion;
    }

    private int readVersion(Map<String, Object> payload) {
        Object raw = payload.get(SCHEMA_VERSION_KEY);
        if (raw == null) {
            return 1;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Blob '" + blobType + "' has non-numeric " + SCHEMA_VERSION_KEY + ": " + text, ex);
        }
    }

    private MigrationReadResult result(Map<String, Object> payload, ReadStatus status, int sourceVersion) {
        return new MigrationReadResult(payload, status, sourceVersion, targetVersion, metricLabel(status));
    }

    private String metricLabel(ReadStatus status) {
        String safeType = blobType.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        if (safeType.length() > 32) {
            safeType = safeType.substring(0, 32);
        }
        return "erg_jsonb_" + safeType + "_" + status.metricSuffix;
    }

    public static final class Builder {
        private final String blobType;
        private final List<SchemaMigration> steps = new ArrayList<>();

        private Builder(String blobType) {
            this.blobType = Objects.requireNonNull(blobType, "blobType");
        }

        /** Registers a step upgrading {@code fromVersion} to {@code fromVersion + 1}. */
        public Builder step(int fromVersion, UnaryOperator<Map<String, Object>> transform) {
            Objects.requireNonNull(transform, "transform");
            steps.add(new SchemaMigration() {
                @Override
                public int fromVersion() {
                    return fromVersion;
                }

                @Override
                public Map<String, Object> apply(Map<String, Object> payload) {
                    return transform.apply(payload);
                }
            });
            return this;
        }

        /**
         * Builds a migrator whose target version is {@code 1 + number of steps}. Steps must form a
         * contiguous chain starting at version 1 (1→2, 2→3, …); a gap or duplicate is a wiring bug.
         */
        public JsonbSchemaMigrator build() {
            Map<Integer, SchemaMigration> byFrom = new LinkedHashMap<>();
            for (SchemaMigration step : steps) {
                if (byFrom.put(step.fromVersion(), step) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate migration step from schemaVersion " + step.fromVersion()
                                    + " for blob '" + blobType + "'");
                }
            }
            int target = 1 + steps.size();
            for (int from = 1; from < target; from++) {
                if (!byFrom.containsKey(from)) {
                    throw new IllegalArgumentException(
                            "Missing migration step from schemaVersion " + from + " for blob '" + blobType
                                    + "' — steps must be a contiguous chain starting at version 1");
                }
            }
            return new JsonbSchemaMigrator(blobType, target, byFrom);
        }
    }
}
