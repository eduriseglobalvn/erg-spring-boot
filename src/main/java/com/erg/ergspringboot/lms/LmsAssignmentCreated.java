package com.erg.ergspringboot.lms;

import java.time.OffsetDateTime;

/** Published inside the create transaction; listeners run after commit. Delivery is at-least-once. */
public record LmsAssignmentCreated(
        String eventId,
        String tenantId,
        String assignmentId,
        String actorId,
        OffsetDateTime occurredAt
) {
}
