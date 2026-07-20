package com.erg.ergspringboot.common.response.quiz;

/** Response payloads for LCMS question-bank authoring commands. */
public final class QuestionBankAuthoringResponses {

    private QuestionBankAuthoringResponses() {
    }

    public record SaveQuestionResponse(
            String questionId,
            String status
    ) {
    }

    public record DeleteQuestionResponse(
            String questionId,
            boolean archived
    ) {
    }
}
