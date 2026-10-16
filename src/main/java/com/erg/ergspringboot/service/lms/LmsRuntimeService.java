package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.lms.LmsRuntimeCompatibility;
import com.erg.ergspringboot.lms.LmsRuntimePort;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.quiz.runtime.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * @deprecated Use {@link com.erg.ergspringboot.lms.LmsRuntimePort} through
 *             {@code LmsOperations}; this bean is a compatibility adapter.
 */
@Deprecated
@Service
public class LmsRuntimeService implements LmsRuntimePort {
    private final LmsRuntimePort operations;

    @Autowired
    public LmsRuntimeService(LmsRuntimePort operations) {
        this.operations = operations;
    }

    /** Compatibility constructor retained while legacy tests/callers are migrated. */
    @Deprecated
    public LmsRuntimeService(LmsClassroomService classroomService,
            LmsAssignmentService assignmentService, LmsQuizService quizService,
            LmsAttemptService attemptService, LmsStudentService studentService,
            QuizRuntimePackageService runtimePackageService,
            QuizAttemptGradingService attemptGradingService,
            AttemptEventRecorder attemptEventRecorder, CacheManager cacheManager) {
        this.operations = LmsRuntimeCompatibility.fromLegacyServices(classroomService, assignmentService, quizService,
                attemptService, studentService, runtimePackageService, attemptGradingService,
                attemptEventRecorder, cacheManager);
    }

    @Override public StudentAssignmentListResponse listStudentAssignments(String studentId, UserAccessContext access, int page, int size) { return operations.listStudentAssignments(studentId, access, page, size); }
    @Override public StudentScoreListResponse listStudentScores(String studentId, UserAccessContext access, int page, int size) { return operations.listStudentScores(studentId, access, page, size); }
    @Override public QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access) { return operations.getQuizPackage(quizId, access); }
    @Override public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) { return operations.getQuiz(quizId, access); }
    @Override public AttemptResponse startAttempt(String studentId, StartAttemptRequest request, UserAccessContext access) { return operations.startAttempt(studentId, request, access); }
    @Override public AttemptResponse startAttempt(String studentId, StartAttemptRequest request, String idempotencyKey, UserAccessContext access) { return operations.startAttempt(studentId, request, idempotencyKey, access); }
    @Override public AttemptResponse saveDraft(String attemptId, AttemptDraftRequest request, UserAccessContext access) { return operations.saveDraft(attemptId, request, access); }
    @Override public AnswerResultResponse saveAnswer(String attemptId, String questionId, SaveAnswerRequest request, UserAccessContext access) { return operations.saveAnswer(attemptId, questionId, request, access); }
    @Override public AttemptSyncResponse syncAttempt(String attemptId, AttemptSyncRequest request, UserAccessContext access) { return operations.syncAttempt(attemptId, request, access); }
    @Override public AttemptSubmitResponse submitAttempt(String attemptId, SubmitAttemptRequest request, UserAccessContext access) { return operations.submitAttempt(attemptId, request, access); }
    @Override public AssignmentProgressResponse assignmentProgress(String assignmentId, UserAccessContext access) { return operations.assignmentProgress(assignmentId, access); }
    @Override public java.util.List<AttemptResponse> quizStudents(String quizId, UserAccessContext access) { return operations.quizStudents(quizId, access); }
    @Override public EducationUnitListResponse listEducationUnits(UserAccessContext access) { return operations.listEducationUnits(access); }
    @Override public ClassesBySchoolResponse listClassesBySchool(String schoolId, UserAccessContext access) { return operations.listClassesBySchool(schoolId, access); }
}
