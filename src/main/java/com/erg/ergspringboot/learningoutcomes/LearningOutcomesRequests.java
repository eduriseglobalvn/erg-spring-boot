package com.erg.ergspringboot.learningoutcomes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

/** Module-owned command contracts; persistence types never cross this boundary. */
public final class LearningOutcomesRequests {
    private LearningOutcomesRequests() {
    }

    public record CreateLearningPathRequest(
            @NotBlank String title,
            String description,
            String educationUnitId,
            String classId) {
    }

    public record PublishLearningPathRequest(@NotNull Long expectedVersion) {
    }

    public record AddPathNodeRequest(
            @NotBlank String contentRefId,
            @NotBlank String contentKind,
            @NotNull Integer sortOrder,
            boolean required,
            @NotNull Long expectedPathVersion) {
    }

    public record CreateCompetencyRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            String educationUnitId) {
    }

    public record RecordAttainmentRequest(
            @NotBlank String learnerId,
            @NotBlank String competencyId,
            String learningPathId,
            String classId,
            @NotBlank String evidenceRef,
            Integer progressPercent) {
        public RecordAttainmentRequest(String learnerId, String competencyId, String learningPathId,
                                       String classId, String evidenceRef) {
            this(learnerId, competencyId, learningPathId, classId, evidenceRef, 100);
        }
    }

    public record IssueCertificateRequest(
            @NotBlank String learnerId,
            @NotBlank String learningPathId,
            @NotBlank String certificateNumber,
            @NotBlank String evidenceRef,
            OffsetDateTime expiresAt) {
    }

    public record EvaluateCompletionRequest(
            @NotBlank String learnerId,
            @NotNull Long expectedVersion,
            @NotNull List<String> completedContentRefIds,
            @NotBlank String evidenceRef) {
        public EvaluateCompletionRequest {
            completedContentRefIds = completedContentRefIds == null ? List.of() : List.copyOf(completedContentRefIds);
        }
    }

    public record RevokeCertificateRequest(
            @NotNull Long expectedVersion,
            @NotBlank String reason) {
    }
}
