package com.erg.ergspringboot.learningstandards;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Transport-neutral commands owned by the learning-standards module. */
public final class LearningStandardsRequests {
    private LearningStandardsRequests() {
    }

    public record CreatePackageRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 32) String standard,
            @NotBlank @Size(max = 96) String packageVersion,
            @NotBlank @Size(max = 255) String artifactKey,
            @NotBlank @Size(max = 64) String artifactSha256,
            @NotNull @Positive @Max(536_870_912) Long byteSize,
            @NotBlank @Size(max = 64) String manifestSha256,
            @NotBlank @Size(max = 255) String launchPath) {
    }

    public record ValidatePackageRequest(
            @NotNull Long expectedVersion,
            @NotBlank @Size(max = ScormPackageValidator.MAX_MANIFEST_CHARS) String manifestXml) {
    }

    public record CreateLtiRegistrationRequest(
            @NotBlank @Size(max = 255) String issuer,
            @NotBlank @Size(max = 255) String clientId,
            @NotBlank @Size(max = 255) String deploymentId,
            @NotBlank @Size(max = 16_384) String publicKeyPem,
            @NotBlank @Size(max = 255) String allowedTargetHost) {
    }

    public record LtiLaunchRequest(
            @NotBlank @Size(max = 64_000) String signedToken,
            @NotBlank @Size(max = 255) String nonce) {
    }

    public record XapiStatementRequest(
            @NotBlank @Size(max = 96) String statementId,
            @NotBlank @Size(max = XapiStatementPolicy.MAX_FIELD_LENGTH) String actorId,
            @NotBlank @Size(max = XapiStatementPolicy.MAX_FIELD_LENGTH) String verb,
            @NotBlank @Size(max = XapiStatementPolicy.MAX_FIELD_LENGTH) String objectId,
            @NotBlank @Size(max = 64) String payloadSha256,
            @Size(max = 64) String occurredAt) {
    }
}
