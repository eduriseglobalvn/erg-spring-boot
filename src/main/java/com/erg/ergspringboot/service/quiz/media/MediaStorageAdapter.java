package com.erg.ergspringboot.service.quiz.media;

/**
 * Object-storage abstraction for quiz media (spec §5). Presign hands the client a short-lived,
 * direct-to-storage upload target; commit is validated server-side afterwards.
 *
 * <p>The repo has R2/S3 configuration but no wired S3 SDK yet, so {@link LocalMediaStorageAdapter}
 * provides a content-addressed local implementation. A real R2/S3 adapter can replace it without
 * touching {@link MediaService} — the contract is the storage key layout plus the two URLs.
 */
public interface MediaStorageAdapter {

    /** Deterministic content-addressed object key for a tenant's asset bytes. */
    String storageKey(String tenantId, String sha256, String mimeType);

    /** Short-lived (~5 min) URL the client PUTs raw bytes to. */
    String presignUpload(String storageKey, String mimeType, long byteSize);

    /** Public/CDN URL used to read committed bytes. Immutable, content-hash keyed. */
    String publicUrl(String storageKey);
}
