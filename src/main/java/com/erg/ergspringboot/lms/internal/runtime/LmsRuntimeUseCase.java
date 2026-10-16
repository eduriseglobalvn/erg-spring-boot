package com.erg.ergspringboot.lms.internal.runtime;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.lms.LmsRuntimePort;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.internal.classroom.LmsClassroomQueryUseCase;
import com.erg.ergspringboot.lms.internal.readmodel.LmsStudentReadModelUseCase;
import com.erg.ergspringboot.lms.internal.resource.LmsResourceQueryUseCase;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;

/** Composes LMS runtime use cases behind the module's public application port. */
@Service
@Primary
public class LmsRuntimeUseCase implements LmsRuntimePort {
    private final LmsAttemptUseCase attempts;
    private final LmsClassroomQueryUseCase classrooms;
    private final LmsResourceQueryUseCase resources;
    private final LmsStudentReadModelUseCase students;

    public LmsRuntimeUseCase(LmsAttemptUseCase attempts, LmsClassroomQueryUseCase classrooms,
            LmsResourceQueryUseCase resources, LmsStudentReadModelUseCase students) {
        this.attempts = attempts;
        this.classrooms = classrooms;
        this.resources = resources;
        this.students = students;
    }

    @Override public StudentAssignmentListResponse listStudentAssignments(String studentId, UserAccessContext access, int page, int size) { return students.listAssignments(studentId, access, page, size); }
    @Override public StudentScoreListResponse listStudentScores(String studentId, UserAccessContext access, int page, int size) { return students.listScores(studentId, access, page, size); }
    @Override public QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access) { return resources.getQuizPackage(quizId, access); }
    @Override public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) { return resources.getQuiz(quizId, access); }
    @Override public AttemptResponse startAttempt(String studentId, StartAttemptRequest request, UserAccessContext access) { return attempts.start(studentId, request, null, access); }
    @Override public AttemptResponse startAttempt(String studentId, StartAttemptRequest request, String idempotencyKey, UserAccessContext access) { return attempts.start(studentId, request, idempotencyKey, access); }
    @Override public AttemptResponse saveDraft(String attemptId, AttemptDraftRequest request, UserAccessContext access) { return attempts.saveDraft(attemptId, request, access); }
    @Override public AnswerResultResponse saveAnswer(String attemptId, String questionId, SaveAnswerRequest request, UserAccessContext access) { return attempts.saveAnswer(attemptId, questionId, request, access); }
    @Override public AttemptSyncResponse syncAttempt(String attemptId, AttemptSyncRequest request, UserAccessContext access) { return attempts.sync(attemptId, request, access); }
    @Override public AttemptSubmitResponse submitAttempt(String attemptId, SubmitAttemptRequest request, UserAccessContext access) { return attempts.submit(attemptId, request, access); }
    @Override public AssignmentProgressResponse assignmentProgress(String assignmentId, UserAccessContext access) { return students.assignmentProgress(assignmentId, access); }
    @Override public List<AttemptResponse> quizStudents(String quizId, UserAccessContext access) { return students.quizStudents(quizId, access); }
    @Override public EducationUnitListResponse listEducationUnits(UserAccessContext access) { return classrooms.listEducationUnits(access); }
    @Override public ClassesBySchoolResponse listClassesBySchool(String schoolId, UserAccessContext access) { return classrooms.listClassesBySchool(schoolId, access); }
}
