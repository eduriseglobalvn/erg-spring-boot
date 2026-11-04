package com.erg.ergspringboot.lcms.quiz.publish;

import java.math.BigDecimal;

/** Server-side grade of one answer against its answer key, produced by the kind's {@link QuestionKindStrategy}. */
public record GradeResult(
        Boolean correct,
        boolean partiallyRight,
        BigDecimal awardedPoints,
        boolean needsManualGrade
) {
}
