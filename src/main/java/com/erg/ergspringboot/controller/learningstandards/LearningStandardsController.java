package com.erg.ergspringboot.controller.learningstandards;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsOperations;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.CreateLtiRegistrationRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.CreatePackageRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.LtiLaunchRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.ValidatePackageRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.XapiStatementRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.LtiLaunchResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.LtiRegistrationResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.PackageResponse;
import com.erg.ergspringboot.learningstandards.LearningStandardsResponses.XapiStatementResponse;
import com.erg.ergspringboot.learningstandards.authorization.LearningStandardsLaunch;
import com.erg.ergspringboot.learningstandards.authorization.LearningStandardsRead;
import com.erg.ergspringboot.learningstandards.authorization.LearningStandardsWrite;
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
@RequestMapping("/api/v1/learning-standards")
public class LearningStandardsController {
    private final LearningStandardsOperations operations;

    public LearningStandardsController(LearningStandardsOperations operations) {
        this.operations = operations;
    }

    @PostMapping("/packages")
    @LearningStandardsWrite
    public ApiResponse<PackageResponse> createPackage(
            @Valid @RequestBody CreatePackageRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Learning-standard package registered", operations.createPackage(
                UserAccessContext.from(request), body, idempotencyKey, correlation(request)), request);
    }

    @GetMapping("/packages")
    @LearningStandardsRead
    public ApiResponse<List<PackageResponse>> packages(
            @RequestParam(required = false) String status, HttpServletRequest request) {
        return ok("Learning-standard packages loaded", operations.packages(UserAccessContext.from(request), status), request);
    }

    @GetMapping("/packages/{packageId}")
    @LearningStandardsRead
    public ApiResponse<PackageResponse> packageById(@PathVariable String packageId, HttpServletRequest request) {
        return ok("Learning-standard package loaded", operations.packageById(UserAccessContext.from(request), packageId), request);
    }

    @PostMapping("/packages/{packageId}/validate")
    @LearningStandardsWrite
    public ApiResponse<PackageResponse> validatePackage(
            @PathVariable String packageId,
            @Valid @RequestBody ValidatePackageRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Learning-standard package validated", operations.validatePackage(
                UserAccessContext.from(request), packageId, body, idempotencyKey, correlation(request)), request);
    }

    @GetMapping("/packages/{packageId}/launch")
    @LearningStandardsLaunch
    public ApiResponse<PackageResponse> launchPackage(@PathVariable String packageId, HttpServletRequest request) {
        return ok("Learning-standard package launch resolved", operations.launchPackage(
                UserAccessContext.from(request), packageId), request);
    }

    @PostMapping("/lti/registrations")
    @LearningStandardsWrite
    public ApiResponse<LtiRegistrationResponse> createLtiRegistration(
            @Valid @RequestBody CreateLtiRegistrationRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("LTI registration created", operations.createLtiRegistration(
                UserAccessContext.from(request), body, idempotencyKey, correlation(request)), request);
    }

    @PostMapping("/lti/registrations/{registrationId}/launch")
    @LearningStandardsLaunch
    public ApiResponse<LtiLaunchResponse> launchLti(
            @PathVariable String registrationId,
            @Valid @RequestBody LtiLaunchRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("LTI launch accepted", operations.launchLti(
                UserAccessContext.from(request), registrationId, body, idempotencyKey, correlation(request)), request);
    }

    @PostMapping("/xapi/statements")
    @LearningStandardsWrite
    public ApiResponse<XapiStatementResponse> ingestXapi(
            @Valid @RequestBody XapiStatementRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("xAPI statement accepted", operations.ingestXapi(
                UserAccessContext.from(request), body, idempotencyKey, correlation(request)), request);
    }

    private String correlation(HttpServletRequest request) {
        return RequestCorrelation.current(request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), correlation(request));
    }
}
