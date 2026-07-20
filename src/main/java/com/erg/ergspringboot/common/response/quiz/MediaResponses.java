package com.erg.ergspringboot.common.response.quiz;

/**
 * Media presign/commit response payloads (spec §4.3, §5). {@code deduped} tells the client whether the
 * bytes already existed (reuse the returned id/url) or must be uploaded to {@code uploadUrl} first.
 */
public final class MediaResponses {

    private MediaResponses() {
    }

    public record MediaPresignResponse(
            boolean deduped,
            String mediaId,
            String url,
            String uploadUrl,
            String storageKey
    ) {
        public static MediaPresignResponse deduped(String mediaId, String url) {
            return new MediaPresignResponse(true, mediaId, url, null, null);
        }

        public static MediaPresignResponse upload(String mediaId, String uploadUrl, String storageKey) {
            return new MediaPresignResponse(false, mediaId, null, uploadUrl, storageKey);
        }
    }

    public record MediaCommitResponse(
            String mediaId,
            String url,
            Integer width,
            Integer height
    ) {
    }
}
