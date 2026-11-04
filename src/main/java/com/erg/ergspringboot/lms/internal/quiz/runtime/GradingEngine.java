package com.erg.ergspringboot.lms.internal.quiz.runtime;

import com.erg.ergspringboot.lcms.quiz.publish.GradeResult;
import com.erg.ergspringboot.lcms.quiz.publish.QuestionKindRegistry;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Server-side grading of a single answer against its answer key (spec §6). Pure and deterministic: takes the
 * runtime kind, the raw student answer, the question's answer key and max points, and returns a graded result.
 *
 * <p>The per-kind rules live in the kind's {@link com.erg.ergspringboot.lcms.quiz.publish.QuestionKindStrategy},
 * registered in {@link QuestionKindRegistry}; this component only resolves the strategy, failing closed on an
 * unknown kind.
 */
@Component
public class GradingEngine {

    public GradeResult grade(String kind, Object rawAnswer, Map<String, Object> answerKey, BigDecimal maxPoints) {
        return QuestionKindRegistry.requireRuntimeKind(kind).strategy().grade(rawAnswer, answerKey, maxPoints);
    }
}
