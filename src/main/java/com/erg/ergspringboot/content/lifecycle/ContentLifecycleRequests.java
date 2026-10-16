package com.erg.ergspringboot.content.lifecycle;

import jakarta.validation.constraints.NotBlank;

public final class ContentLifecycleRequests {
    private ContentLifecycleRequests() { }

    public record TransitionRequest(
            @NotBlank String targetState,
            Long expectedVersion,
            String reason,
            @NotBlank String idempotencyKey,
            String operationId,
            String sourceVersionId
    ) { }
}
