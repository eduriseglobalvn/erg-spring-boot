package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.internal.classroom.LmsClassroomQueryUseCase;
import com.erg.ergspringboot.lms.internal.readmodel.LmsStudentReadModelUseCase;
import com.erg.ergspringboot.lms.internal.resource.LmsResourceQueryUseCase;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.quiz.runtime.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/** Compatibility facade for LMS runtime APIs. Use-case logic lives under {@code lms.internal}. */
@Service
public class LmsRuntimeService {
    private final LmsAttemptUseCase attempts;
    private final LmsClassroomQueryUseCase classrooms;
    private final LmsResourceQueryUseCase resources;
    private final LmsStudentReadModelUseCase students;

    @Autowired
    public LmsRuntimeService(LmsAttemptUseCase attempts, LmsClassroomQueryUseCase classrooms,
            LmsResourceQueryUseCase resources, LmsStudentReadModelUseCase students) {
        this.attempts = attempts;
        this.classrooms = classrooms;
        this.resources = resources;
        this.students = students;
    }

    /** Compatibility constructor retained while legacy tests/callers are migrated. */
    public LmsRuntimeService(LmsClassroomService classroomService,
            LmsAssignmentService assignmentService, LmsQuizService quizService,
            LmsAttemptService attemptService, LmsStudentService studentService,
            QuizRuntimePackageService runtimePackageService,
            QuizAttemptGradingService attemptGradingService,
            AttemptEventRecorder attemptEventRecorder, CacheManager cacheManager) {
        this(new LmsAttemptUseCase(classroomService, assignmentService, quizService, attemptService,
                        studentService, runtimePackageService, attemptGradingService,
                        attemptEventRecorder, cacheManager),
                new LmsClassroomQueryUseCase(classroomService),
                new LmsResourceQueryUseCase(quizService, runtimePackageService),
                new LmsStudentReadModelUseCase(classroomService, assignmentService,
                        attemptService, studentService, quizService));
    }

    public StudentAssignmentListResponse listStudentAssignments(String studentId, UserAccessContext access) {
        return students.listAssignments(studentId, access);
    }
    public StudentAssignmentListResponse listStudentAssignments(String studentId, UserAccessContext access,
            int page, int size) { return students.listAssignments(studentId, access, page, size); }
    public StudentScoreListResponse listStudentScores(String studentId, UserAccessContext access) {
        return students.listScores(studentId, access);
    }
    public StudentScoreListResponse listStudentScores(String studentId, UserAccessContext access,
            int page, int size) { return students.listScores(studentId, access, page, size); }
    public QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access) {
        return resources.getQuizPackage(quizId, access);
    }
    public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) {
        return resources.getQuiz(quizId, access);
    }
    public AttemptResponse startAttempt(String studentId, StartAttemptRequest request,
            UserAccessContext access) { return attempts.start(studentId, request, null, access); }
    public AttemptResponse startAttempt(String studentId, StartAttemptRequest request,
            String idempotencyKey, UserAccessContext access) {
        return attempts.start(studentId, request, idempotencyKey, access);
    }
    public AttemptResponse saveDraft(String attemptId, AttemptDraftRequest request,
            UserAccessContext access) { return attempts.saveDraft(attemptId, request, access); }
    public AnswerResultResponse saveAnswer(String attemptId, String questionId,
            SaveAnswerRequest request, UserAccessContext access) {
        return attempts.saveAnswer(attemptId, questionId, request, access);
    }
    public AttemptSyncResponse syncAttempt(String attemptId, AttemptSyncRequest request,
            UserAccessContext access) { return attempts.sync(attemptId, request, access); }
    public AttemptSubmitResponse submitAttempt(String attemptId, SubmitAttemptRequest request,
            UserAccessContext access) { return attempts.submit(attemptId, request, access); }
    public AssignmentProgressResponse assignmentProgress(String assignmentId, UserAccessContext access) {
        return students.assignmentProgress(assignmentId, access);
    }
    public List<AttemptResponse> quizStudents(String quizId, UserAccessContext access) {
        return students.quizStudents(quizId, access);
    }
    public EducationUnitListResponse listEducationUnits(UserAccessContext access) {
        return classrooms.listEducationUnits(access);
    }
    public ClassesBySchoolResponse listClassesBySchool(String schoolId, UserAccessContext access) {
        return classrooms.listClassesBySchool(schoolId, access);
    }
}
