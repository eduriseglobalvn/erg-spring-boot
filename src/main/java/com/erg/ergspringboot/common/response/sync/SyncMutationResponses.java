package com.erg.ergspringboot.common.response.sync;

import java.time.OffsetDateTime;

/** Wire-level acknowledgement and conflict payloads shared by offline-capable mutations. */
public final class SyncMutationResponses {
    private SyncMutationResponses() { }

    public record MutationAck(
            String operationId,
            String idempotencyKey,
            String entityType,
            String entityId,
            String status,
            long serverVersion,
            OffsetDateTime serverTime,
            boolean replayed
    ) { }

    public record ConflictPayload(
            String code,
            String operationId,
            String idempotencyKey,
            String entityType,
            String entityId,
            Long expectedVersion,
            Long actualVersion,
            OffsetDateTime serverTime,
            boolean retryable
    ) { }
}
