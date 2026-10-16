package com.erg.ergspringboot.learningoutcomes;

import java.time.OffsetDateTime;
import java.util.List;

/** Stable scalar response DTOs for the learning-outcomes module. */
public final class LearningOutcomesResponses {
    private LearningOutcomesResponses() {
    }

    public record LearningPathResponse(
            String id,
            String tenantId,
            String title,
            String description,
            String status,
            String educationUnitId,
            String classId,
            long recordVersion,
            int nodeCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record PathNodeResponse(
            String id,
            String tenantId,
            String pathId,
            String contentRefId,
            String contentKind,
            int sortOrder,
            boolean required,
            OffsetDateTime createdAt) {
    }

    public record CompetencyResponse(
            String id,
            String tenantId,
            String code,
            String name,
            String description,
            String status,
            String educationUnitId,
            long recordVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record AttainmentResponse(
            String id,
            String tenantId,
            String learnerId,
            String competencyId,
            String learningPathId,
            String classId,
            String status,
            String evidenceRef,
            OffsetDateTime achievedAt,
            long recordVersion,
            int progressPercent) {
        public AttainmentResponse(String id, String tenantId, String learnerId, String competencyId,
                                   String learningPathId, String classId, String status, String evidenceRef,
                                   OffsetDateTime achievedAt, long recordVersion) {
            this(id, tenantId, learnerId, competencyId, learningPathId, classId, status, evidenceRef,
                    achievedAt, recordVersion, 100);
        }
    }

    public record CertificateResponse(
            String id,
            String tenantId,
            String learnerId,
            String learningPathId,
            String certificateNumber,
            String verificationId,
            String status,
            String evidenceRef,
            OffsetDateTime issuedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime revokedAt,
            long recordVersion,
            String revokedBy,
            String revocationReason) {
        public CertificateResponse(String id, String tenantId, String learnerId, String learningPathId,
                                   String certificateNumber, String status, String evidenceRef,
                                   OffsetDateTime issuedAt, OffsetDateTime expiresAt, OffsetDateTime revokedAt,
                                   long recordVersion) {
            this(id, tenantId, learnerId, learningPathId, certificateNumber, null, status, evidenceRef,
                    issuedAt, expiresAt, revokedAt, recordVersion, null, null);
        }
    }

    public record CompletionResponse(
            String id,
            String tenantId,
            String learnerId,
            String learningPathId,
            String status,
            int requiredNodeCount,
            int completedNodeCount,
            int progressPercent,
            String evidenceRef,
            OffsetDateTime evaluatedAt,
            long recordVersion) {
    }

    public record PathDetailResponse(LearningPathResponse path, List<PathNodeResponse> nodes) {
        public PathDetailResponse {
            nodes = List.copyOf(nodes == null ? List.of() : nodes);
        }
    }
}
