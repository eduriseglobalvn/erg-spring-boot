package com.erg.ergspringboot.common.request.quiz;

/**
 * Media presign/commit request payloads (spec §4.3, §5). The FE hashes bytes client-side, asks for a
 * presigned upload target, PUTs the bytes, then commits so the server can verify and expose a URL.
 */
public final class MediaRequests {

    private MediaRequests() {
    }

    public record MediaPresignRequest(
            String sha256,
            String mimeType,
            Long byteSize,
            String originalName
    ) {
    }

    public record MediaCommitRequest(
            String mediaId,
            Integer width,
            Integer height
    ) {
    }
}
