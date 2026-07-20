package com.erg.ergspringboot.common.response.quiz;

/**
 * Authoring write response payloads (spec §4.2). Create/fork return the new draft handle; autosave
 * echoes the incremented {@code docVersion} so the client can keep its optimistic lock in sync.
 */
public final class QuizAuthoringResponses {

    private QuizAuthoringResponses() {
    }

    public record CreateQuizResponse(
            String quizId,
            String draftId,
            long docVersion
    ) {
    }

    public record SaveDraftResponse(
            long docVersion
    ) {
    }

    public record ValidationResultResponse(
            boolean valid,
            java.util.List<ValidationErrorResponse> errors
    ) {
    }

    public record ValidationErrorResponse(
            String path,
            String code,
            String message
    ) {
    }

    public record PublishResponse(
            String quizVersionId,
            String versionLabel,
            int versionNumber,
            String packageId,
            String contentHash
    ) {
    }

    public record ForkResponse(
            String draftId,
            long docVersion,
            String baseVersionLabel
    ) {
    }

    public record DeleteQuizResponse(
            String quizId,
            boolean deleted
    ) {
    }
}
