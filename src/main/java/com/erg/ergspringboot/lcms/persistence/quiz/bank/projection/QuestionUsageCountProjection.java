package com.erg.ergspringboot.lcms.persistence.quiz.bank.projection;

public interface QuestionUsageCountProjection {

    String getQuestionId();

    Long getUsageCount();
}
