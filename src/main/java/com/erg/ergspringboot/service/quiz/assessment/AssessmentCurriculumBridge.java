package com.erg.ergspringboot.service.quiz.assessment;

import com.erg.ergspringboot.model.lms.Quiz;
import java.util.Map;

/**
 * Assessment-facing curriculum contract.
 *
 * <p>Temporary bridge: until Agent 2/3 canonical curriculum repositories are available, the
 * implementation reads the existing {@code lms_*} curriculum tables without renaming them.
 */
public interface AssessmentCurriculumBridge {

    void validateQuestionPlacement(String tenantId, String subjectId, String levelId, String topicId);

    void validateQuizPlacement(String tenantId, String subjectId, String levelId, String topicId);

    Map<String, Object> snapshotForQuiz(String tenantId, Quiz quiz);
}
