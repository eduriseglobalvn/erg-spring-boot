package com.erg.ergspringboot.common.request.quiz;

import java.util.List;
import java.util.Map;

/**
 * Authoring write payloads (spec §4.2). Create returns a fresh draft; draft autosave is guarded by an
 * optimistic {@code docVersion} lock so concurrent editors get a 409 instead of silently overwriting.
 */
public final class QuizAuthoringRequests {

    private QuizAuthoringRequests() {
    }

    public record CreateQuizRequest(
            String title,
            String subtitle,
            String kind,
            String subjectId,
            String levelId,
            String categoryId,
            List<String> questionIds
    ) {
    }

    /** Full-document autosave (PUT). {@code docVersion} must equal the server's current value. */
    public record SaveDraftRequest(
            Long docVersion,
            Map<String, Object> settings,
            Map<String, Object> playerTemplate,
            Object tree
    ) {
    }

    public record SlideUpsertRequest(
            Long docVersion,
            String groupId,
            Map<String, Object> slide,
            String afterSlideId
    ) {
    }

    public record ReorderRequest(
            Long docVersion,
            List<ReorderGroup> groups
    ) {
        public record ReorderGroup(String groupId, List<String> slideIds) {
        }
    }

    /** Publish (spec §4.2). {@code docVersion} guards the draft; {@code X-Idempotency-Key} is mandatory. */
    public record PublishRequest(
            Long docVersion,
            String gradingMode,
            String versionLabel
    ) {
    }
}
