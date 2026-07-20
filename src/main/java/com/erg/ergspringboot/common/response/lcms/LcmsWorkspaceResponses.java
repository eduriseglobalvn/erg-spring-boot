package com.erg.ergspringboot.common.response.lcms;

import com.erg.ergspringboot.common.response.PageResponse;
import java.util.List;

public final class LcmsWorkspaceResponses {

    private LcmsWorkspaceResponses() {
    }

    public record QuestionBankWorkspaceResponse(
            String tenantId,
            PageResponse<QuestionBankQuestionRowResponse> questions,
            List<QuestionBankOptionResponse> subjects,
            List<QuestionBankOptionResponse> levels,
            List<QuestionBankOptionResponse> topics,
            List<QuestionBankQuizOptionResponse> quizzes
    ) {
    }

    public record QuestionBankQuestionRowResponse(
            String id,
            String scopeType,
            String scopeId,
            String scopeLabel,
            String subjectId,
            String subjectLabel,
            String levelId,
            String levelLabel,
            String topicId,
            String categoryLabel,
            String gradeLabel,
            String title,
            String objective,
            String type,
            String difficulty,
            String status,
            List<String> tags,
            List<QuestionBankChoiceResponse> choices,
            String answer,
            Double masteryRate,
            long usageCount,
            Integer schoolsUsing,
            String lastUsedAt,
            String recommendedCluster,
            String rationale,
            String updatedAt
    ) {
    }

    public record QuestionBankChoiceResponse(
            String id,
            String label,
            boolean correct
    ) {
    }

    public record QuestionBankOptionResponse(
            String id,
            String label,
            String subjectId,
            String levelId,
            String description,
            String scopeLabel,
            List<String> categoryIds
    ) {
    }

    public record QuestionBankQuizOptionResponse(
            String id,
            String title,
            String status,
            String kind,
            String subjectId,
            String levelId,
            String categoryId,
            List<String> questionIds,
            int questionCount,
            String durationLabel,
            String scopeLabel,
            String sourceMode,
            String ownerLabel,
            String updatedAt
    ) {
    }

    public record AccessManagementWorkspaceResponse(
            String tenantId,
            PageResponse<UserAccessRowResponse> users,
            List<RoleOptionResponse> roleOptions,
            List<ScopeOptionResponse> scopeOptions,
            boolean effectivePermissionsIncluded
    ) {
    }

    public record UserAccessRowResponse(
            String userId,
            String fullName,
            String email,
            String status,
            long roleCount,
            long scopeCount,
            List<EffectivePermissionResponse> effectivePermissions
    ) {
    }

    public record RoleOptionResponse(
            String id,
            String name
    ) {
    }

    public record ScopeOptionResponse(
            String id,
            String label,
            String kind
    ) {
    }

    public record EffectivePermissionResponse(
            String permission,
            String source
    ) {
    }
}
