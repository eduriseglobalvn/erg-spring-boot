package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;

/** Narrow application port for LMS runtime commands and read models. */
public interface LmsRuntimePort {
    StudentAssignmentListResponse listStudentAssignments(String studentId, UserAccessContext access, int page, int size);
    StudentScoreListResponse listStudentScores(String studentId, UserAccessContext access, int page, int size);
    QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access);
    QuizDetailResponse getQuiz(String quizId, UserAccessContext access);
    AttemptResponse startAttempt(String studentId, StartAttemptRequest request, UserAccessContext access);
    AttemptResponse startAttempt(String studentId, StartAttemptRequest request, String idempotencyKey, UserAccessContext access);
    AttemptResponse saveDraft(String attemptId, AttemptDraftRequest request, UserAccessContext access);
    AnswerResultResponse saveAnswer(String attemptId, String questionId, SaveAnswerRequest request, UserAccessContext access);
    AttemptSyncResponse syncAttempt(String attemptId, AttemptSyncRequest request, UserAccessContext access);
    AttemptSubmitResponse submitAttempt(String attemptId, SubmitAttemptRequest request, UserAccessContext access);
    AssignmentProgressResponse assignmentProgress(String assignmentId, UserAccessContext access);
    List<AttemptResponse> quizStudents(String quizId, UserAccessContext access);
    EducationUnitListResponse listEducationUnits(UserAccessContext access);
    ClassesBySchoolResponse listClassesBySchool(String schoolId, UserAccessContext access);
}
