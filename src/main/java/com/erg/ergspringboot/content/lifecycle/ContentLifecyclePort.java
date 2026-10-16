package com.erg.ergspringboot.content.lifecycle;

import com.erg.ergspringboot.content.lifecycle.ContentLifecycleRequests.TransitionRequest;
import com.erg.ergspringboot.content.lifecycle.ContentLifecycleResponses.LifecycleResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;

/** Public contract consumed by authoring modules when a publish snapshot is committed. */
public interface ContentLifecyclePort {
    LifecycleResponse transition(
            UserAccessContext access,
            LifecycleResourceKind kind,
            String resourceId,
            TransitionRequest request
    );

    void recordPublishedSnapshot(String tenantId, String resourceId, String actorId, String idempotencyKey);
}
