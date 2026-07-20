package com.erg.ergspringboot.repository.lms.projection;

public record QuestionBankOptionProjection(
        String id,
        String label,
        String subjectId,
        String levelId,
        String description
) {
    public QuestionBankOptionProjection(String id, String label) {
        this(id, label, null, null, null);
    }
}
