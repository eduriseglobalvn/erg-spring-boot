package com.erg.ergspringboot.repository.lms.projection;

public interface QuestionUsageCountProjection {

    String getQuestionId();

    Long getUsageCount();
}
