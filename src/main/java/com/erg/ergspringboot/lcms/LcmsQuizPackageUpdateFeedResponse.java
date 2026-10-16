package com.erg.ergspringboot.lcms;

import java.util.List;

/** Bounded update feed for portable exam packages in one tenant and channel. */
public record LcmsQuizPackageUpdateFeedResponse(
        String feedVersion,
        String tenantId,
        String quizId,
        String releaseChannel,
        Integer currentVersionNumber,
        String runtimeVersion,
        LcmsQuizPackageManifest latest,
        List<LcmsQuizPackageManifest> available,
        String etag,
        String generatedAt
) {
    public LcmsQuizPackageUpdateFeedResponse {
        available = available == null ? List.of() : List.copyOf(available);
    }
}
