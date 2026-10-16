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
            Object tree,
            String operationId,
            String idempotencyKey
    ) {
        public SaveDraftRequest(Long docVersion, Map<String, Object> settings,
                                Map<String, Object> playerTemplate, Object tree) {
            this(docVersion, settings, playerTemplate, tree, null, null);
        }
        public SaveDraftRequest withIdempotencyKey(String key) {
            return new SaveDraftRequest(docVersion, settings, playerTemplate, tree, operationId, key);
        }
    }

    public record SlideUpsertRequest(
            Long docVersion,
            String groupId,
            Map<String, Object> slide,
            String afterSlideId,
            String operationId,
            String idempotencyKey
    ) {
        public SlideUpsertRequest(Long docVersion, String groupId, Map<String, Object> slide, String afterSlideId) {
            this(docVersion, groupId, slide, afterSlideId, null, null);
        }
        public SlideUpsertRequest withIdempotencyKey(String key) {
            return new SlideUpsertRequest(docVersion, groupId, slide, afterSlideId, operationId, key);
        }
    }

    public record ReorderRequest(
            Long docVersion,
            List<ReorderGroup> groups,
            String operationId,
            String idempotencyKey
    ) {
        public ReorderRequest(Long docVersion, List<ReorderGroup> groups) {
            this(docVersion, groups, null, null);
        }
        public ReorderRequest withIdempotencyKey(String key) {
            return new ReorderRequest(docVersion, groups, operationId, key);
        }
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
