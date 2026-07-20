package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSubmitResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AnswerResultResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AssignmentProgressResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptDraftRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSyncRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSyncResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ClassesBySchoolResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.EducationUnitListResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizDetailResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SaveAnswerRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentAssignmentListResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentScoreListResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.CreateAssignmentRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.StudentGroupRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.UpdateAssignmentRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.UpdateRecipientsRequest;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.CreateAssignmentResponse;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.StudentGroupResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsAssignmentCommandService;
import com.erg.ergspringboot.service.lms.LmsRuntimeService;
import java.util.List;
import org.springframework.stereotype.Service;

/** Public LMS command facade for the attempt hot path. */
@Service
public class LmsOperations {
    private final LmsRuntimeService legacy;
    private final LmsAssignmentCommandService assignments;

    public LmsOperations(LmsRuntimeService legacy, LmsAssignmentCommandService assignments) {
        this.legacy = legacy;
        this.assignments = assignments;
    }

    public AttemptResponse startAttempt(String studentId, StartAttemptRequest request,
            String idempotencyKey, UserAccessContext access) {
        return legacy.startAttempt(studentId, request, idempotencyKey, access);
    }

    public AttemptSubmitResponse submitAttempt(String attemptId, SubmitAttemptRequest request,
            UserAccessContext access) {
        return legacy.submitAttempt(attemptId, request, access);
    }

    public CreateAssignmentResponse createAssignment(String tenantId, String userId, String idempotencyKey,
            CreateAssignmentRequest request) {
        return assignments.create(tenantId, userId, idempotencyKey, request);
    }

    public void updateAssignment(String tenantId, String assignmentId, UpdateAssignmentRequest request) {
        assignments.updateAssignment(tenantId, assignmentId, request);
    }

    public CreateAssignmentResponse updateAssignmentRecipients(String tenantId, String assignmentId,
            UpdateRecipientsRequest request) {
        return assignments.updateRecipients(tenantId, assignmentId, request);
    }

    public void deleteAssignment(String tenantId, String assignmentId) {
        assignments.delete(tenantId, assignmentId);
    }

    public StudentGroupResponse createStudentGroup(String tenantId, String userId, StudentGroupRequest request) {
        return assignments.createGroup(tenantId, userId, request);
    }

    public StudentGroupResponse updateStudentGroup(String tenantId, String groupId, StudentGroupRequest request) {
        return assignments.updateGroup(tenantId, groupId, request);
    }

    public void deleteStudentGroup(String tenantId, String groupId) {
        assignments.deleteGroup(tenantId, groupId);
    }

    public StudentAssignmentListResponse listStudentAssignments(String studentId, UserAccessContext access,
            int page, int size) { return legacy.listStudentAssignments(studentId, access, page, size); }

    public StudentScoreListResponse listStudentScores(String studentId, UserAccessContext access, int page, int size) {
        return legacy.listStudentScores(studentId, access, page, size);
    }

    public QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access) {
        return legacy.getQuizPackage(quizId, access);
    }

    public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) { return legacy.getQuiz(quizId, access); }

    public AttemptResponse saveDraft(String attemptId, AttemptDraftRequest request, UserAccessContext access) {
        return legacy.saveDraft(attemptId, request, access);
    }

    public AnswerResultResponse saveAnswer(String attemptId, String questionId, SaveAnswerRequest request,
            UserAccessContext access) { return legacy.saveAnswer(attemptId, questionId, request, access); }

    public AttemptSyncResponse syncAttempt(String attemptId, AttemptSyncRequest request, UserAccessContext access) {
        return legacy.syncAttempt(attemptId, request, access);
    }

    public List<AttemptResponse> quizStudents(String quizId, UserAccessContext access) {
        return legacy.quizStudents(quizId, access);
    }

    public AssignmentProgressResponse assignmentProgress(String assignmentId, UserAccessContext access) {
        return legacy.assignmentProgress(assignmentId, access);
    }

    public EducationUnitListResponse listEducationUnits(UserAccessContext access) { return legacy.listEducationUnits(access); }

    public ClassesBySchoolResponse listClassesBySchool(String schoolId, UserAccessContext access) {
        return legacy.listClassesBySchool(schoolId, access);
    }
}
