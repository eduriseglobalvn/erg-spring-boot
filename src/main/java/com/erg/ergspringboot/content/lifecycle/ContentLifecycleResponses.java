package com.erg.ergspringboot.content.lifecycle;

import java.time.OffsetDateTime;

public final class ContentLifecycleResponses {
    private ContentLifecycleResponses() { }

    public record LifecycleResponse(
            LifecycleResourceKind resourceKind,
            String resourceId,
            String tenantId,
            LifecycleState previousState,
            LifecycleState state,
            long version,
            String actorId,
            String reason,
            boolean replayed,
            String sourceVersionId,
            OffsetDateTime transitionedAt
    ) { }
}
