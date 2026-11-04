package com.erg.ergspringboot.learningstandards.internal;

import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.LtiRegistrationResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.PackageResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.XapiStatementResponse;
import com.erg.ergspringboot.learningstandards.persistence.LearningPackage;
import com.erg.ergspringboot.learningstandards.persistence.LtiRegistration;
import com.erg.ergspringboot.learningstandards.persistence.XapiStatement;

/** Maps learning-standards entities to the published response DTOs. */
final class LearningStandardsResponseMapper {

    private LearningStandardsResponseMapper() {
    }

    static PackageResponse packageResponse(LearningPackage value) {
        return new PackageResponse(value.getId(), value.getTenantId(), value.getTitle(), value.getStandard(),
                value.getPackageVersion(), value.getArtifactKey(), value.getArtifactSha256(), value.getByteSize(),
                value.getManifestSha256(), value.getManifestIdentifier(), value.getLaunchPath(), value.getStatus(),
                value.getQuarantineReason(), version(value), value.getCreatedAt(), value.getUpdatedAt());
    }

    static LtiRegistrationResponse ltiResponse(LtiRegistration value) {
        return new LtiRegistrationResponse(value.getId(), value.getTenantId(), value.getIssuer(), value.getClientId(),
                value.getDeploymentId(), value.getAllowedTargetHost(), value.getStatus(), version(value),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    static XapiStatementResponse xapiResponse(XapiStatement value) {
        return new XapiStatementResponse(value.getId(), value.getTenantId(), value.getStatementId(), value.getActorHash(),
                value.getVerb(), value.getObjectId(), value.getPayloadSha256(), value.getStatus(), value.getOccurredAt(),
                value.getReceivedAt(), version(value));
    }

    static long version(Object value) {
        return switch (value) {
            case LearningPackage packageValue -> packageValue.getRecordVersion() == null ? 0 : packageValue.getRecordVersion();
            case LtiRegistration registration -> registration.getRecordVersion() == null ? 0 : registration.getRecordVersion();
            case XapiStatement statement -> statement.getRecordVersion() == null ? 0 : statement.getRecordVersion();
            default -> 0;
        };
    }
}
