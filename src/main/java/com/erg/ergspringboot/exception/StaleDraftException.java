package com.erg.ergspringboot.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an autosave/publish carries a {@code docVersion} that no longer matches the server's
 * current draft version (spec §4.2, §8 optimistic lock). The client should reload the draft and merge.
 * The authoritative server version is included in the message so the client can resync.
 */
public class StaleDraftException extends ApiException {

    private final long serverDocVersion;

    public StaleDraftException(long serverDocVersion) {
        super(
                HttpStatus.CONFLICT,
                "STALE_DRAFT",
                "Stale Draft",
                "draft was modified by another write; reload before saving (serverDocVersion=" + serverDocVersion + ")"
        );
        this.serverDocVersion = serverDocVersion;
    }

    public long getServerDocVersion() {
        return serverDocVersion;
    }
}
