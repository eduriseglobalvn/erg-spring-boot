package com.erg.ergspringboot.lcms.persistence.quiz.bank.projection;

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
