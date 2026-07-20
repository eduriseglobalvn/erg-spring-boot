package com.erg.ergspringboot.controller.lms;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AnswerResultResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AssignmentProgressResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptDraftRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ClassesBySchoolResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.EducationUnitListResponse;
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
import com.erg.ergspringboot.lms.LmsOperations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lms")
public class LmsController {

    private final LmsRuntimeService lmsRuntimeService;
    private final LmsOperations lms;

    public LmsController(LmsRuntimeService lmsRuntimeService, LmsOperations lms) {
        this.lmsRuntimeService = lmsRuntimeService;
        this.lms = lms;
    }

    @GetMapping("/students/me/assignments")
    public ApiResponse<StudentAssignmentListResponse> assignments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        StudentAssignmentListResponse data = lmsRuntimeService.listStudentAssignments(
                access.userId(), access, page == null ? 0 : page, size == null ? 50 : size);
        return ok("Assignments loaded", data, request);
    }

    @GetMapping("/students/me/scores")
    public ApiResponse<StudentScoreListResponse> scores(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        StudentScoreListResponse data = lmsRuntimeService.listStudentScores(
                access.userId(), access, page == null ? 0 : page, size == null ? 50 : size);
        return ok("Scores loaded", data, request);
    }

    @GetMapping("/quizzes/{quizId}/package")
    public ResponseEntity<ApiResponse<QuizPackageResponse>> quizPackage(@PathVariable String quizId, HttpServletRequest request) {
        QuizPackageResponse data = lmsRuntimeService.getQuizPackage(quizId, UserAccessContext.from(request));
        String eTag = packageETag(data);
        CacheControl cacheControl = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate().mustRevalidate();
        if (matchesETag(request.getHeader(HttpHeaders.IF_NONE_MATCH), eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(cacheControl)
                    .eTag(eTag)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .eTag(eTag)
                .body(ok("Quiz package loaded", data, request));
    }

    @GetMapping("/quizzes/{quizId}")
    public ApiResponse<QuizDetailResponse> quiz(@PathVariable String quizId, HttpServletRequest request) {
        return ok("Quiz loaded", lmsRuntimeService.getQuiz(quizId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts")
    public ApiResponse<AttemptResponse> startAttempt(
            @Valid @RequestBody StartAttemptRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Attempt started", lms.startAttempt(access.userId(), body, idempotencyKey, access), request);
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
        return ok("Attempt submitted", lms.submitAttempt(attemptId, body, UserAccessContext.from(request)), request);
    }

    @GetMapping("/quizzes/{quizId}/students")
    public ApiResponse<List<AttemptResponse>> quizStudents(@PathVariable String quizId, HttpServletRequest request) {
        return ok("Quiz students loaded", lmsRuntimeService.quizStudents(quizId, UserAccessContext.from(request)), request);
    }

    @GetMapping("/assignments/{assignmentId}/progress")
    public ApiResponse<AssignmentProgressResponse> assignmentProgress(@PathVariable String assignmentId, HttpServletRequest request) {
        return ok("Assignment progress loaded", lmsRuntimeService.assignmentProgress(assignmentId, UserAccessContext.from(request)), request);
    }

    @GetMapping("/education-units")
    public ApiResponse<EducationUnitListResponse> educationUnits(HttpServletRequest request) {
        return ok("Education units loaded", lmsRuntimeService.listEducationUnits(UserAccessContext.from(request)), request);
    }

    @GetMapping("/classes")
    public ApiResponse<ClassesBySchoolResponse> classes(@RequestParam(required = false) String schoolId, HttpServletRequest request) {
        // Classes are intentionally fetched by selected school instead of being bundled into session bootstrap.
        return ok("Classes loaded", lmsRuntimeService.listClassesBySchool(schoolId, UserAccessContext.from(request)), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }

    private String packageETag(QuizPackageResponse data) {
        String value = data == null || data.contentHash() == null || data.contentHash().isBlank()
                ? "empty"
                : data.contentHash();
        return "\"" + value.replace("\"", "") + "\"";
    }

    private boolean matchesETag(String ifNoneMatch, String eTag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String normalized = candidate.trim();
            if ("*".equals(normalized) || normalized.equals(eTag)) {
                return true;
            }
        }
        return false;
    }
}
