package com.erg.ergspringboot.elearning;

import java.util.List;

/**
 * Published inside the writing transaction (listeners run after commit, at-least-once): somebody should hear about a
 * discussion reply or an inbox message. {@code eventId} doubles as the notification idempotency key.
 */
public record ElearningNotice(
        String eventId,
        String tenantId,
        String eventType,
        String sourceType,
        String sourceId,
        String title,
        String preview,
        String actorId,
        List<String> recipientUserIds
) {
}
