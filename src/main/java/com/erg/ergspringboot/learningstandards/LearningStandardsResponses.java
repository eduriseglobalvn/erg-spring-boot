package com.erg.ergspringboot.learningstandards;

import java.time.OffsetDateTime;

/** Stable scalar response DTOs; no JPA entity crosses a transport boundary. */
public final class LearningStandardsResponses {
    private LearningStandardsResponses() {
    }

    public record PackageResponse(
            String id,
            String tenantId,
            String title,
            String standard,
            String packageVersion,
            String artifactKey,
            String artifactSha256,
            long byteSize,
            String manifestSha256,
            String manifestIdentifier,
            String launchPath,
            String status,
            String quarantineReason,
            long recordVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record LtiRegistrationResponse(
            String id,
            String tenantId,
            String issuer,
            String clientId,
            String deploymentId,
            String allowedTargetHost,
            String status,
            long recordVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record LtiLaunchResponse(
            String registrationId,
            String subject,
            String targetLinkUri,
            String status,
            OffsetDateTime acceptedAt) {
    }

    public record XapiStatementResponse(
            String id,
            String tenantId,
            String statementId,
            String actorHash,
            String verb,
            String objectId,
            String payloadSha256,
            String status,
            OffsetDateTime occurredAt,
            OffsetDateTime receivedAt,
            long recordVersion) {
    }
}
