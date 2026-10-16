package com.erg.ergspringboot.platform.persistence;

import java.util.List;

/**
 * Explicit inventory of JSONB fields. This is metadata, not a universal serializer or schema
 * framework: the owning bounded context still owns its reader and migration chain.
 */
public final class JsonbDurabilityCatalog {

    public enum Classification {
        DURABLE_VERSIONED,
        DURABLE_OWNER_READER,
        TRANSIENT_EVENT,
        EXEMPT_AUDIT_OR_PII
    }

    public record Field(String owner, String entity, String property, Classification classification, String reason) {
        public String key() {
            return entity + "." + property;
        }
    }

    private static final List<Field> FIELDS = List.of(
            field("crm", "CrmHandover", "checklist", Classification.DURABLE_OWNER_READER, "workflow snapshot"),
            field("crm", "CrmSchoolDeal", "revenueLines", Classification.DURABLE_OWNER_READER, "commercial line items"),
            field("crm", "CrmSchoolDeal", "costLines", Classification.DURABLE_OWNER_READER, "commercial line items"),
            field("crm", "CrmSchoolProfile", "risks", Classification.DURABLE_OWNER_READER, "risk register"),
            field("learningcontent", "ContentAsset", "metadata", Classification.DURABLE_OWNER_READER, "asset metadata"),
            field("learningcontent", "ContentItemAsset", "metadata", Classification.DURABLE_OWNER_READER, "asset metadata"),
            field("lms", "Assignment", "studentIds", Classification.DURABLE_OWNER_READER, "recipient relation snapshot"),
            field("lms", "Attempt", "answers", Classification.DURABLE_OWNER_READER, "learner answer document"),
            field("lms", "Attempt", "events", Classification.TRANSIENT_EVENT, "append-only client event batch"),
            field("lms", "Attempt", "client", Classification.TRANSIENT_EVENT, "client diagnostics"),
            field("lms", "Question", "scope", Classification.DURABLE_OWNER_READER, "content scope"),
            field("lms", "Question", "tags", Classification.DURABLE_OWNER_READER, "question metadata"),
            field("lms", "Question", "choices", Classification.DURABLE_OWNER_READER, "question options"),
            field("lms", "Question", "answer", Classification.DURABLE_OWNER_READER, "question answer key"),
            field("lms", "Question", "content", Classification.DURABLE_OWNER_READER, "question content"),
            field("lms", "Question", "settings", Classification.DURABLE_OWNER_READER, "question settings"),
            field("lms", "Question", "metadata", Classification.DURABLE_OWNER_READER, "question metadata"),
            field("lms", "Quiz", "scope", Classification.DURABLE_OWNER_READER, "quiz scope"),
            field("lms", "Quiz", "topicIds", Classification.DURABLE_OWNER_READER, "topic relation snapshot"),
            field("lms", "Quiz", "questionIds", Classification.DURABLE_OWNER_READER, "question relation snapshot"),
            field("lms", "Quiz", "slides", Classification.DURABLE_OWNER_READER, "legacy authoring content"),
            field("lms", "Quiz", "settings", Classification.DURABLE_OWNER_READER, "legacy quiz settings"),
            field("lms", "Quiz", "result", Classification.DURABLE_OWNER_READER, "legacy result settings"),
            field("lms", "Quiz", "theme", Classification.DURABLE_OWNER_READER, "legacy theme settings"),
            field("lms", "Quiz", "contentScope", Classification.DURABLE_OWNER_READER, "legacy content scope"),
            field("lms", "QuizQuestion", "settings", Classification.DURABLE_OWNER_READER, "quiz question settings"),
            field("lms", "QuizQuestion", "metadata", Classification.DURABLE_OWNER_READER, "quiz question metadata"),
            field("lms", "Student", "metrics", Classification.DURABLE_OWNER_READER, "student metrics snapshot"),
            field("lms", "Subject", "scope", Classification.DURABLE_OWNER_READER, "subject scope"),
            field("notifications", "NotificationMessageEntity", "payloadData", Classification.TRANSIENT_EVENT, "delivery payload"),
            field("notifications", "NotificationOutboxEntity", "payload", Classification.TRANSIENT_EVENT, "delivery outbox payload"),
            field("notifications", "NotificationPreferenceEntity", "quietHours", Classification.DURABLE_OWNER_READER, "preference document"),
            field("platform.events", "OutboxEvent", "payload", Classification.TRANSIENT_EVENT, "event envelope"),
            field("quiz", "QuizDraft", "settings", Classification.DURABLE_VERSIONED, "authoring document"),
            field("quiz", "QuizDraft", "playerTemplate", Classification.DURABLE_VERSIONED, "authoring document"),
            field("quiz", "QuizDraft", "tree", Classification.DURABLE_VERSIONED, "authoring document"),
            field("quiz", "QuizBankItem", "questionIds", Classification.DURABLE_OWNER_READER, "bank relation snapshot"),
            field("quiz", "AttemptAnswerRow", "rawAnswer", Classification.TRANSIENT_EVENT, "normalized attempt row"),
            field("quiz", "AttemptEvent", "answer", Classification.TRANSIENT_EVENT, "attempt event"),
            field("quiz", "AttemptEvent", "result", Classification.TRANSIENT_EVENT, "attempt event result"),
            field("quiz", "AttemptEvent", "meta", Classification.TRANSIENT_EVENT, "attempt event metadata"),
            field("quiz", "QuizPackage", "assetsManifest", Classification.DURABLE_VERSIONED, "published package"),
            field("quiz", "QuizPackage", "packageJson", Classification.DURABLE_VERSIONED, "published package"),
            field("quiz", "QuizVersion", "authoringSnapshot", Classification.DURABLE_VERSIONED, "immutable publish snapshot"),
            field("quiz", "QuizVersion", "runtimeQuiz", Classification.DURABLE_VERSIONED, "immutable runtime"),
            field("quiz", "QuizVersion", "answerKey", Classification.DURABLE_VERSIONED, "immutable grading key"),
            field("quiz", "QuizVersionQuestion", "body", Classification.DURABLE_VERSIONED, "immutable question snapshot"),
            field("quiz", "QuizVersionQuestion", "answerKey", Classification.DURABLE_VERSIONED, "immutable grading key"),
            field("reporting", "ReportJob", "requestJson", Classification.DURABLE_OWNER_READER, "async job request"),
            field("reporting", "ReportJob", "resultJson", Classification.DURABLE_OWNER_READER, "async job result"),
            field("users", "EmployeeProfile", "emergencyContact", Classification.EXEMPT_AUDIT_OR_PII, "PII owner-specific payload"),
            field("users", "UserActivity", "metadata", Classification.EXEMPT_AUDIT_OR_PII, "audit metadata")
    );

    private JsonbDurabilityCatalog() { }

    public static List<Field> all() {
        return FIELDS;
    }

    private static Field field(String owner, String entity, String property, Classification classification, String reason) {
        return new Field(owner, entity, property, classification, reason);
    }
}
