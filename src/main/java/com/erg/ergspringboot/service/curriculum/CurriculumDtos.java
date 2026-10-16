package com.erg.ergspringboot.service.curriculum;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

public final class CurriculumDtos {

    private CurriculumDtos() {
    }

    public record CurriculumNodeRequest(
            String code,
            String name,
            String description,
            String status,
            Integer sortOrder
    ) {
    }

    public record CurriculumSubjectResponse(
            String id,
            String tenantId,
            String code,
            String name,
            String description,
            String status,
            Integer sortOrder,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record CurriculumLevelResponse(
            String id,
            String tenantId,
            String subjectId,
            String code,
            String name,
            String description,
            String status,
            Integer sortOrder,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record CurriculumTopicResponse(
            String id,
            String tenantId,
            String levelId,
            String subjectId,
            String code,
            String name,
            String description,
            String status,
            Integer sortOrder,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record CurriculumTreeResponse(List<CurriculumSubjectTree> subjects) {
    }

    public record CurriculumSubjectTree(
            CurriculumSubjectResponse subject,
            List<CurriculumLevelTree> levels
    ) {
    }

    public record CurriculumLevelTree(
            CurriculumLevelResponse level,
            List<CurriculumTopicResponse> topics
    ) {
    }

    public record CurriculumImpactResponse(
            String nodeId,
            String nodeType,
            boolean canHardDelete,
            boolean canArchive,
            CurriculumImpactCounts impact,
            List<String> recommendedActions,
            String todo
    ) {
    }

    public record CurriculumImpactCounts(
            long questions,
            long quizzes,
            long publishedQuizVersions,
            long contentItems,
            long assignments,
            long studentAttempts,
            long childLevels,
            long childTopics
    ) {
    }

    public record CurriculumHardDeleteResponse(
            String nodeId,
            String nodeType,
            boolean deleted
    ) {
    }

    public record CurriculumTeachingPlanRequest(
            LocalDate startDate,
            List<Integer> teachingDays,
            Integer sessionsPerDay,
            String timezone,
            String status,
            Long expectedVersion
    ) { }

    public record CurriculumTeachingPlanResponse(
            String id,
            String tenantId,
            String schoolId,
            String subjectId,
            String academicYear,
            LocalDate startDate,
            List<Integer> teachingDays,
            Integer sessionsPerDay,
            String timezone,
            String status,
            Long recordVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) { }

    public record CurriculumProgramRowRequest(
            String id, String topic, Integer period, String title, Integer sessions, String status) { }

    public record CurriculumProgramRequest(
            String name, String status, List<CurriculumProgramRowRequest> rows, Long expectedVersion) { }

    public record CurriculumProgramRowResponse(
            String id, String topic, Integer period, String title, Integer sessions,
            Integer sortOrder, String status, Long recordVersion) { }

    public record CurriculumProgramResponse(
            String id, String tenantId, String subjectId, String levelId, String academicYear,
            String name, String status, Long recordVersion, List<CurriculumProgramRowResponse> rows,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
}
