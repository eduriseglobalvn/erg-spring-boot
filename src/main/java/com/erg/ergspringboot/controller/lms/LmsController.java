package com.erg.ergspringboot.controller.lms;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AnswerResultResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AssignmentProgressResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptDraftRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSubmitResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSyncRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSyncResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizDetailResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SaveAnswerRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentAssignmentListResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentScoreListResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lms")
public class LmsController {

    private final LmsRuntimeService lmsRuntimeService;

    public LmsController(LmsRuntimeService lmsRuntimeService) {
        this.lmsRuntimeService = lmsRuntimeService;
    }

    @GetMapping("/students/me/assignments")
    public ApiResponse<StudentAssignmentListResponse> assignments(HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Assignments loaded", lmsRuntimeService.listStudentAssignments(access.userId(), access), request);
    }

    @GetMapping("/students/me/scores")
    public ApiResponse<StudentScoreListResponse> scores(HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Scores loaded", lmsRuntimeService.listStudentScores(access.userId(), access), request);
    }

    @GetMapping("/quizzes/{quizId}/package")
    public ApiResponse<QuizPackageResponse> quizPackage(@PathVariable String quizId, HttpServletRequest request) {
        return ok("Quiz package loaded", lmsRuntimeService.getQuizPackage(quizId, UserAccessContext.from(request)), request);
    }

    @GetMapping("/quizzes/{quizId}")
    public ApiResponse<QuizDetailResponse> quiz(@PathVariable String quizId, HttpServletRequest request) {
        return ok("Quiz loaded", lmsRuntimeService.getQuiz(quizId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts")
    public ApiResponse<AttemptResponse> startAttempt(@Valid @RequestBody StartAttemptRequest body, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Attempt started", lmsRuntimeService.startAttempt(access.userId(), body, access), request);
    }

    @PatchMapping("/attempts/{attemptId}/draft")
    public ApiResponse<AttemptResponse> saveDraft(
            @PathVariable String attemptId,
            @RequestBody AttemptDraftRequest body,
            HttpServletRequest request
    ) {
        return ok("Draft saved", lmsRuntimeService.saveDraft(attemptId, body, UserAccessContext.from(request)), request);
    }

    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    public ApiResponse<AnswerResultResponse> saveAnswer(
            @PathVariable String attemptId,
            @PathVariable String questionId,
            @RequestBody SaveAnswerRequest body,
            HttpServletRequest request
    ) {
        return ok("Answer saved", lmsRuntimeService.saveAnswer(attemptId, questionId, body, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts/{attemptId}/answers")
    public ApiResponse<AttemptResponse> saveAnswers(
            @PathVariable String attemptId,
            @RequestBody AttemptDraftRequest body,
            HttpServletRequest request
    ) {
        return ok("Answers saved", lmsRuntimeService.saveDraft(attemptId, body, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts/{attemptId}/sync")
    public ApiResponse<AttemptSyncResponse> syncAttempt(
            @PathVariable String attemptId,
            @Valid @RequestBody AttemptSyncRequest body,
            HttpServletRequest request
    ) {
        return ok("Attempt synced", lmsRuntimeService.syncAttempt(attemptId, body, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ApiResponse<AttemptSubmitResponse> submitAttempt(
            @PathVariable String attemptId,
            @RequestBody SubmitAttemptRequest body,
            HttpServletRequest request
    ) {
        return ok("Attempt submitted", lmsRuntimeService.submitAttempt(attemptId, body, UserAccessContext.from(request)), request);
    }

    @GetMapping("/quizzes/{quizId}/students")
    public ApiResponse<List<AttemptResponse>> quizStudents(@PathVariable String quizId, HttpServletRequest request) {
        return ok("Quiz students loaded", lmsRuntimeService.quizStudents(quizId, UserAccessContext.from(request)), request);
    }

    @GetMapping("/assignments/{assignmentId}/progress")
    public ApiResponse<AssignmentProgressResponse> assignmentProgress(@PathVariable String assignmentId, HttpServletRequest request) {
        return ok("Assignment progress loaded", lmsRuntimeService.assignmentProgress(assignmentId, UserAccessContext.from(request)), request);
    }

    @GetMapping({"/scopes/me", "/centers", "/education-units"})
    public ApiResponse<Map<String, Object>> deprecatedScopeReads(HttpServletRequest request) {
        // These endpoints are kept as lightweight compatibility responses; FE should use /api/v1/sessions/current.
        return ok("Use session bootstrap instead", lmsRuntimeService.deprecatedBootstrapReplacement(request.getRequestURI(), UserAccessContext.from(request)), request);
    }

    @GetMapping("/classes")
    public ApiResponse<Map<String, Object>> classes(@RequestParam(required = false) String schoolId, HttpServletRequest request) {
        // Classes are intentionally fetched by selected school instead of being bundled into session bootstrap.
        return ok("Classes loaded", lmsRuntimeService.listClassesBySchool(schoolId, UserAccessContext.from(request)), request);
    }

    @GetMapping({
            "/access-management/options", "/access-management/scopes", "/access-management/users",
            "/dashboard/overview", "/dashboard/interventions", "/students",
            "/subjects", "/levels", "/topics", "/questions", "/quizzes", "/assignments", "/reports/classroom",
            "/discussions", "/announcements", "/internal-documents", "/imports/jobs"
    })
    public ApiResponse<Map<String, Object>> adminReads(HttpServletRequest request) {
        return ok("LMS data loaded", lmsRuntimeService.adminPlaceholder(request.getRequestURI(), UserAccessContext.from(request)), request);
    }

    @PutMapping("/scopes/current")
    public ApiResponse<Map<String, Object>> updateCurrentScope(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Scope updated", Map.of("scope", lmsRuntimeService.normalizeScope(body, access)), request);
    }

    @PostMapping({
            "/access-management/preview", "/centers", "/classes", "/students", "/students/bulk-accounts",
            "/imports/google-sheet/tabs", "/imports/google-sheet/preview", "/imports/google-sheet/commit",
            "/questions", "/questions/random-pick", "/quizzes", "/quizzes/from-questions", "/quizzes/random",
            "/assignments", "/assignments/deliveries", "/discussions", "/discussions/replies",
            "/moderation/check", "/announcements", "/internal-documents"
    })
    public ApiResponse<Map<String, Object>> adminCreates(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("LMS operation accepted", lmsRuntimeService.acceptAdminOperation(body, access), request);
    }

    @PutMapping({"/access-management/users/{userId}/access", "/classes/{id}", "/students/{id}", "/quizzes/{id}/publish"})
    public ApiResponse<Map<String, Object>> adminUpdates(
            @PathVariable(required = false) String userId,
            @PathVariable(required = false) String id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("LMS operation updated", lmsRuntimeService.acceptAdminUpdate(id == null ? userId : id, body, access), request);
    }

    @GetMapping("/reports/export")
    public ApiResponse<Map<String, Object>> reportExport(@RequestParam Map<String, String> query, HttpServletRequest request) {
        return ok("Report export prepared", lmsRuntimeService.reportExport(query, UserAccessContext.from(request)), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
