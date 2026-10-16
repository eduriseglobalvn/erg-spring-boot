package com.erg.ergspringboot.learningstandards;

import java.util.Optional;

/**
 * Module-owned object-storage port. Implementations may use local disk, R2 or
 * another S3-compatible store without exposing SDK types to the domain.
 */
public interface LearningStandardsArtifactStorage {
    record ArtifactMetadata(long byteSize, String sha256) {
    }

    Optional<ArtifactMetadata> head(String tenantId, String artifactKey);
}
