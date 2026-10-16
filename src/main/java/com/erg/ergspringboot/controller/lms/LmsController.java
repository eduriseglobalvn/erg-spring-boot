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
import com.erg.ergspringboot.lms.LmsOperations;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentRead;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentReadSelf;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentProgressRead;
import com.erg.ergspringboot.lms.authorization.LmsClassRead;
import com.erg.ergspringboot.lms.authorization.LmsGradeRead;
import com.erg.ergspringboot.lms.authorization.LmsQuizRead;
import com.erg.ergspringboot.lms.authorization.LmsSubmit;
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

    private final LmsOperations lms;

    public LmsController(LmsOperations lms) {
        this.lms = lms;
    }

    @GetMapping("/students/me/assignments")
    @LmsAssignmentReadSelf
    public ApiResponse<StudentAssignmentListResponse> assignments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        StudentAssignmentListResponse data = lms.listStudentAssignments(
                access.userId(), access, page == null ? 0 : page, size == null ? 50 : size);
        return ok("Assignments loaded", data, request);
    }

    @GetMapping("/students/me/scores")
    @LmsAssignmentReadSelf
    public ApiResponse<StudentScoreListResponse> scores(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        StudentScoreListResponse data = lms.listStudentScores(
                access.userId(), access, page == null ? 0 : page, size == null ? 50 : size);
        return ok("Scores loaded", data, request);
    }

    @GetMapping("/quizzes/{quizId}/package")
    @LmsQuizRead
    public ResponseEntity<ApiResponse<QuizPackageResponse>> quizPackage(@PathVariable String quizId, HttpServletRequest request) {
        QuizPackageResponse data = lms.getQuizPackage(quizId, UserAccessContext.from(request));
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
    @LmsQuizRead
    public ApiResponse<QuizDetailResponse> quiz(@PathVariable String quizId, HttpServletRequest request) {
        return ok("Quiz loaded", lms.getQuiz(quizId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts")
    @LmsSubmit
    public ApiResponse<AttemptResponse> startAttempt(
            @Valid @RequestBody StartAttemptRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Attempt started", lms.startAttempt(access.userId(), body, idempotencyKey, access), request);
    }

    @PatchMapping("/attempts/{attemptId}/draft")
    @LmsSubmit
    public ApiResponse<AttemptResponse> saveDraft(
            @PathVariable String attemptId,
            @RequestBody AttemptDraftRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        AttemptDraftRequest effectiveBody = body == null || idempotencyKey == null || idempotencyKey.isBlank()
                ? body
                : body.withIdempotencyKey(idempotencyKey);
        return ok("Draft remains in client storage", lms.saveDraft(attemptId, effectiveBody, UserAccessContext.from(request)), request);
    }

    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    @LmsSubmit
    public ApiResponse<AnswerResultResponse> saveAnswer(
            @PathVariable String attemptId,
            @PathVariable String questionId,
            @RequestBody SaveAnswerRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        SaveAnswerRequest effectiveBody = body == null || idempotencyKey == null || idempotencyKey.isBlank()
                ? body
                : body.withIdempotencyKey(idempotencyKey);
        return ok("Answer remains in client storage", lms.saveAnswer(attemptId, questionId, effectiveBody, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts/{attemptId}/answers")
    @LmsSubmit
    public ApiResponse<AttemptResponse> saveAnswers(
            @PathVariable String attemptId,
            @RequestBody AttemptDraftRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        AttemptDraftRequest effectiveBody = body == null || idempotencyKey == null || idempotencyKey.isBlank()
                ? body
                : body.withIdempotencyKey(idempotencyKey);
        return ok("Answers remain in client storage", lms.saveDraft(attemptId, effectiveBody, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts/{attemptId}/sync")
    @LmsSubmit
    public ApiResponse<AttemptSyncResponse> syncAttempt(
            @PathVariable String attemptId,
            @Valid @RequestBody AttemptSyncRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        AttemptSyncRequest effectiveBody = body == null || idempotencyKey == null || idempotencyKey.isBlank()
                ? body
                : body.withIdempotencyKey(idempotencyKey);
        return ok("Client state checked", lms.syncAttempt(attemptId, effectiveBody, UserAccessContext.from(request)), request);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @LmsSubmit
    public ApiResponse<AttemptSubmitResponse> submitAttempt(
            @PathVariable String attemptId,
            @RequestBody SubmitAttemptRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        SubmitAttemptRequest effectiveBody = body == null || idempotencyKey == null || idempotencyKey.isBlank()
                ? body
                : body.withIdempotencyKey(idempotencyKey);
        return ok("Attempt submitted", lms.submitAttempt(attemptId, effectiveBody, UserAccessContext.from(request)), request);
    }

    @GetMapping("/quizzes/{quizId}/students")
    @LmsGradeRead
    public ApiResponse<List<AttemptResponse>> quizStudents(@PathVariable String quizId, HttpServletRequest request) {
        return ok("Quiz students loaded", lms.quizStudents(quizId, UserAccessContext.from(request)), request);
    }

    @GetMapping("/assignments/{assignmentId}/progress")
    @LmsAssignmentProgressRead
    public ApiResponse<AssignmentProgressResponse> assignmentProgress(@PathVariable String assignmentId, HttpServletRequest request) {
        return ok("Assignment progress loaded", lms.assignmentProgress(assignmentId, UserAccessContext.from(request)), request);
    }

    @GetMapping("/education-units")
    @LmsClassRead
    public ApiResponse<EducationUnitListResponse> educationUnits(HttpServletRequest request) {
        return ok("Education units loaded", lms.listEducationUnits(UserAccessContext.from(request)), request);
    }

    @GetMapping("/classes")
    @LmsClassRead
    public ApiResponse<ClassesBySchoolResponse> classes(@RequestParam(required = false) String schoolId, HttpServletRequest request) {
        // Classes are intentionally fetched by selected school instead of being bundled into session bootstrap.
        return ok("Classes loaded", lms.listClassesBySchool(schoolId, UserAccessContext.from(request)), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
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
