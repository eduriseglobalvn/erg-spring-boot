package com.erg.ergspringboot.learningcontent.internal;

import com.erg.ergspringboot.common.response.sync.SyncMutationResponses.ConflictPayload;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.SyncConflictException;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared input normalization and idempotent-write receipts for the learning-content collaborators of
 * {@link LearningContentService}. Error messages are part of the REST/GraphQL contract; keep them stable.
 */
final class LearningContentSupport {

    private LearningContentSupport() {
    }

    static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    static String requiredUpper(String value, String fieldName) {
        return required(value, fieldName).toUpperCase(Locale.ROOT);
    }

    static String normalizedOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    static Integer positive(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new BadRequestException(fieldName + " must be positive");
        }
        return value;
    }

    static Long nonNegative(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new BadRequestException(fieldName + " must be non-negative");
        }
        return value;
    }

    static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 100;
        }
        if (limit < 1) {
            throw new BadRequestException("limit must be positive");
        }
        return Math.min(limit, 500);
    }

    /**
     * Optional {@link LmsSyncOperationService} receipts for retryable updates: reserve, replay, complete or
     * abandon, plus the optimistic-version check. Every call is a no-op when the sync service is absent.
     */
    static final class SyncReceipts {

        private final LmsSyncOperationService sync;

        SyncReceipts(LmsSyncOperationService sync) {
            this.sync = sync;
        }

        LmsSyncOperationService.Receipt reserve(String tenantId, String accountId, String operationId,
                                                String idempotencyKey, String entityType, String entityId,
                                                Object payload, Long expectedVersion) {
            return sync == null ? null : sync.reserve(tenantId, accountId, operationId, idempotencyKey,
                    entityType, entityId, payload, expectedVersion).orElse(null);
        }

        <T> T result(LmsSyncOperationService.Receipt receipt, Class<T> type) {
            return sync.result(receipt, type);
        }

        void complete(LmsSyncOperationService.Receipt receipt, Object result, long version) {
            if (receipt != null) sync.complete(receipt, result, version);
        }

        void abandon(LmsSyncOperationService.Receipt receipt) {
            if (receipt != null) sync.abandon(receipt);
        }

        void checkVersion(String operationId, String idempotencyKey, String entityType, String entityId,
                          Long expected, long actual) {
            if (expected != null && expected.longValue() != actual) {
                throw new SyncConflictException(new ConflictPayload("STALE_WRITE", operationId, idempotencyKey,
                        entityType, entityId, expected, actual,
                        sync == null ? OffsetDateTime.now(ZoneOffset.UTC) : sync.serverTime(), true),
                        "Learning content has been updated by another request.");
            }
        }
    }
}
