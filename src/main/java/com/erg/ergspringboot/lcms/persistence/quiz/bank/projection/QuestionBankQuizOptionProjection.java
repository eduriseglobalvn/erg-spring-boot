package com.erg.ergspringboot.lcms.persistence.quiz.bank.projection;

import java.util.List;
import java.time.OffsetDateTime;

public record QuestionBankQuizOptionProjection(
        String id,
        String title,
        String status,
        String kind,
        String subjectId,
        String levelId,
        String categoryId,
        List<String> questionIds,
        String ownerUserId,
        OffsetDateTime updatedAt,
        /** Questions in the active published version; editor-authored quizzes link none in lms_quiz_questions. */
        Long publishedQuestionCount
) {
    public QuestionBankQuizOptionProjection(String id, String title, String status, String kind, String subjectId,
            String levelId, String categoryId, List<String> questionIds, String ownerUserId, OffsetDateTime updatedAt) {
        this(id, title, status, kind, subjectId, levelId, categoryId, questionIds, ownerUserId, updatedAt, null);
    }
}
