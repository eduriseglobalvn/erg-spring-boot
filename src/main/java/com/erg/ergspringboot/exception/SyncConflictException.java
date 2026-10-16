package com.erg.ergspringboot.exception;

import com.erg.ergspringboot.common.response.sync.SyncMutationResponses.ConflictPayload;
import org.springframework.http.HttpStatus;

/** Typed 409 returned when a replay key or optimistic version cannot be accepted. */
public final class SyncConflictException extends ApiException {
    private final ConflictPayload conflict;

    public SyncConflictException(ConflictPayload conflict, String message) {
        super(HttpStatus.CONFLICT, conflict.code(), "Sync Conflict", message);
        this.conflict = conflict;
    }

    public ConflictPayload conflict() {
        return conflict;
    }
}
