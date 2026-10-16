package com.erg.ergspringboot.controller.learningoutcomes;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesOperations;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.AddPathNodeRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateCompetencyRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateLearningPathRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.EvaluateCompletionRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.IssueCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.PublishLearningPathRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RecordAttainmentRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RevokeCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.AttainmentResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CertificateResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompletionResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompetencyResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.LearningPathResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathDetailResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathNodeResponse;
import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesIssueCertificate;
import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesRead;
import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesWrite;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-outcomes")
public class LearningOutcomesController {
    private final LearningOutcomesOperations operations;

    public LearningOutcomesController(LearningOutcomesOperations operations) {
        this.operations = operations;
    }

    @PostMapping("/paths")
    @LearningOutcomesWrite
    public ApiResponse<LearningPathResponse> createPath(
            @Valid @RequestBody CreateLearningPathRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Learning path created", operations.createPath(UserAccessContext.from(request), body,
                idempotencyKey, correlation(request)), request);
    }

    @GetMapping("/paths")
    @LearningOutcomesRead
    public ApiResponse<List<LearningPathResponse>> paths(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String educationUnitId,
            @RequestParam(required = false) String classId,
            HttpServletRequest request) {
        return ok("Learning paths loaded", operations.paths(UserAccessContext.from(request), status,
                educationUnitId, classId), request);
    }

    @PostMapping("/paths/{pathId}/publish")
    @LearningOutcomesWrite
    public ApiResponse<LearningPathResponse> publishPath(
            @PathVariable String pathId,
            @Valid @RequestBody PublishLearningPathRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Learning path published", operations.publishPath(UserAccessContext.from(request), pathId,
                body.expectedVersion(), idempotencyKey, correlation(request)), request);
    }

    @PostMapping("/paths/{pathId}/nodes")
    @LearningOutcomesWrite
    public ApiResponse<PathNodeResponse> addNode(
            @PathVariable String pathId,
            @Valid @RequestBody AddPathNodeRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Learning path node added", operations.addNode(UserAccessContext.from(request), pathId, body,
                idempotencyKey, correlation(request)), request);
    }

    @GetMapping("/paths/{pathId}")
    @LearningOutcomesRead
    public ApiResponse<PathDetailResponse> path(@PathVariable String pathId, HttpServletRequest request) {
        return ok("Learning path loaded", operations.path(UserAccessContext.from(request), pathId), request);
    }

    @PostMapping("/competencies")
    @LearningOutcomesWrite
    public ApiResponse<CompetencyResponse> createCompetency(
            @Valid @RequestBody CreateCompetencyRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Competency created", operations.createCompetency(UserAccessContext.from(request), body,
                idempotencyKey, correlation(request)), request);
    }

    @GetMapping("/competencies/{competencyId}")
    @LearningOutcomesRead
    public ApiResponse<CompetencyResponse> competency(@PathVariable String competencyId, HttpServletRequest request) {
        return ok("Competency loaded", operations.competency(UserAccessContext.from(request), competencyId), request);
    }

    @GetMapping("/competencies")
    @LearningOutcomesRead
    public ApiResponse<List<CompetencyResponse>> competencies(
            @RequestParam(required = false) String educationUnitId,
            HttpServletRequest request) {
        return ok("Competencies loaded", operations.competencies(UserAccessContext.from(request), educationUnitId), request);
    }

    @PostMapping("/attainments")
    @LearningOutcomesWrite
    public ApiResponse<AttainmentResponse> recordAttainment(
            @Valid @RequestBody RecordAttainmentRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Competency attainment recorded", operations.recordAttainment(UserAccessContext.from(request), body,
                idempotencyKey, correlation(request)), request);
    }

    @GetMapping("/learners/{learnerId}/attainments")
    @LearningOutcomesRead
    public ApiResponse<List<AttainmentResponse>> attainments(@PathVariable String learnerId, HttpServletRequest request) {
        return ok("Competency attainments loaded", operations.attainments(UserAccessContext.from(request), learnerId), request);
    }

    @PostMapping("/paths/{pathId}/completion")
    @LearningOutcomesWrite
    public ApiResponse<CompletionResponse> evaluateCompletion(
            @PathVariable String pathId,
            @Valid @RequestBody EvaluateCompletionRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Learning path completion evaluated", operations.evaluateCompletion(UserAccessContext.from(request), pathId,
                body, idempotencyKey, correlation(request)), request);
    }

    @GetMapping("/learners/{learnerId}/completions")
    @LearningOutcomesRead
    public ApiResponse<List<CompletionResponse>> completions(@PathVariable String learnerId, HttpServletRequest request) {
        return ok("Learning path completions loaded", operations.completions(UserAccessContext.from(request), learnerId), request);
    }

    @PostMapping("/certificates")
    @LearningOutcomesIssueCertificate
    public ApiResponse<CertificateResponse> issueCertificate(
            @Valid @RequestBody IssueCertificateRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Outcome certificate issued", operations.issueCertificate(UserAccessContext.from(request), body,
                idempotencyKey, correlation(request)), request);
    }

    @PostMapping("/certificates/{certificateId}/revoke")
    @LearningOutcomesIssueCertificate
    public ApiResponse<CertificateResponse> revokeCertificate(
            @PathVariable String certificateId,
            @Valid @RequestBody RevokeCertificateRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Outcome certificate revoked", operations.revokeCertificate(UserAccessContext.from(request), certificateId,
                body, idempotencyKey, correlation(request)), request);
    }

    @GetMapping("/certificates/verify/{verificationId}")
    @LearningOutcomesRead
    public ApiResponse<CertificateResponse> verifyCertificate(@PathVariable String verificationId,
                                                               HttpServletRequest request) {
        return ok("Outcome certificate verified", operations.verifyCertificate(UserAccessContext.from(request),
                verificationId), request);
    }

    @GetMapping("/learners/{learnerId}/certificates")
    @LearningOutcomesRead
    public ApiResponse<List<CertificateResponse>> certificates(@PathVariable String learnerId, HttpServletRequest request) {
        return ok("Outcome certificates loaded", operations.certificates(UserAccessContext.from(request), learnerId), request);
    }

    private String correlation(HttpServletRequest request) {
        return RequestCorrelation.current(request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), correlation(request));
    }
}
