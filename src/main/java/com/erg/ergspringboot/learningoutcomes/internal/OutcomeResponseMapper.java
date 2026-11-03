package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.ISSUED;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.now;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.version;

import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.AttainmentResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CertificateResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompletionResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompetencyResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.LearningPathResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathNodeResponse;
import com.erg.ergspringboot.model.learningoutcomes.Competency;
import com.erg.ergspringboot.model.learningoutcomes.CompetencyAttainment;
import com.erg.ergspringboot.model.learningoutcomes.LearningPath;
import com.erg.ergspringboot.model.learningoutcomes.LearningPathCompletion;
import com.erg.ergspringboot.model.learningoutcomes.LearningPathNode;
import com.erg.ergspringboot.model.learningoutcomes.OutcomeCertificate;

/** Maps learning-outcome entities to the published response DTOs. */
final class OutcomeResponseMapper {
    private OutcomeResponseMapper() {
    }

    static LearningPathResponse pathResponse(LearningPath path, int nodeCount) {
        return new LearningPathResponse(path.getId(), path.getTenantId(), path.getTitle(), path.getDescription(),
                path.getStatus(), path.getEducationUnitId(), path.getClassId(), version(path), nodeCount,
                path.getCreatedAt(), path.getUpdatedAt());
    }

    static PathNodeResponse nodeResponse(LearningPathNode node) {
        return new PathNodeResponse(node.getId(), node.getTenantId(), node.getPathId(), node.getContentRefId(),
                node.getContentKind(), node.getSortOrder(), node.isRequired(), node.getCreatedAt());
    }

    static CompetencyResponse competencyResponse(Competency competency) {
        return new CompetencyResponse(competency.getId(), competency.getTenantId(), competency.getCode(),
                competency.getName(), competency.getDescription(), competency.getStatus(), competency.getEducationUnitId(),
                version(competency), competency.getCreatedAt(), competency.getUpdatedAt());
    }

    static AttainmentResponse attainmentResponse(CompetencyAttainment attainment) {
        return new AttainmentResponse(attainment.getId(), attainment.getTenantId(), attainment.getLearnerId(),
                attainment.getCompetencyId(), attainment.getLearningPathId(), attainment.getClassId(), attainment.getStatus(),
                attainment.getEvidenceRef(), attainment.getAchievedAt(), version(attainment),
                attainment.getProgressPercent() == null ? 0 : attainment.getProgressPercent());
    }

    static CertificateResponse certificateResponse(OutcomeCertificate certificate) {
        return new CertificateResponse(certificate.getId(), certificate.getTenantId(), certificate.getLearnerId(),
                certificate.getLearningPathId(), certificate.getCertificateNumber(), certificate.getVerificationId(),
                effectiveCertificateStatus(certificate), certificate.getEvidenceRef(), certificate.getIssuedAt(), certificate.getExpiresAt(),
                certificate.getRevokedAt(), version(certificate), certificate.getRevokedBy(),
                certificate.getRevocationReason());
    }

    static CompletionResponse completionResponse(LearningPathCompletion completion) {
        return new CompletionResponse(completion.getId(), completion.getTenantId(), completion.getLearnerId(),
                completion.getLearningPathId(), completion.getStatus(), completion.getRequiredNodeCount(),
                completion.getCompletedNodeCount(), completion.getProgressPercent(), completion.getEvidenceRef(),
                completion.getEvaluatedAt(), version(completion));
    }

    private static String effectiveCertificateStatus(OutcomeCertificate certificate) {
        if (ISSUED.equals(certificate.getStatus()) && certificate.getExpiresAt() != null
                && !certificate.getExpiresAt().isAfter(now())) return "EXPIRED";
        return certificate.getStatus();
    }
}
