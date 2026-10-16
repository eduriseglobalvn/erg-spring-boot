package com.erg.ergspringboot.controller.lcms;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lcms.LcmsOperations;
import com.erg.ergspringboot.lcms.LcmsQuizBundleResponse;
import com.erg.ergspringboot.lcms.LcmsQuizBundleRevokeRequest;
import com.erg.ergspringboot.lcms.LcmsQuizPackageManifest;
import com.erg.ergspringboot.lcms.LcmsQuizPackageUpdateFeedResponse;
import jakarta.validation.Valid;
import com.erg.ergspringboot.lcms.authorization.LcmsQuizBundleRead;
import com.erg.ergspringboot.lcms.authorization.LcmsQuizPublish;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portable offline quiz bundle export (spec §7). Returns a signed, self-contained JSON bundle a
 * disconnected player can verify and run. The answer key is embedded only for client-first packages;
 * server-authoritative bundles omit it (cannot be graded offline).
 */
@RestController
public class LcmsQuizBundleController {

    private final LcmsOperations lcms;

    public LcmsQuizBundleController(LcmsOperations lcms) {
        this.lcms = lcms;
    }

    @GetMapping("/api/v1/lcms/quizzes/{quizId}/bundle")
    @LcmsQuizBundleRead
    public ResponseEntity<ApiResponse<LcmsQuizBundleResponse>> exportActive(
            @PathVariable String quizId,
        HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        LcmsQuizBundleResponse data = lcms.exportActiveBundle(access.tenantId(), quizId);
        return cached("Quiz bundle exported", data, data.etag(), request);
    }

    @GetMapping("/api/v1/lcms/quiz-versions/{quizVersionId}/bundle")
    @LcmsQuizBundleRead
    public ResponseEntity<ApiResponse<LcmsQuizBundleResponse>> exportVersion(
            @PathVariable String quizVersionId,
        HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        LcmsQuizBundleResponse data = lcms.exportVersionBundle(access.tenantId(), quizVersionId);
        return cached("Quiz bundle exported", data, data.etag(), request);
    }

    @GetMapping("/api/v1/lcms/quiz-packages/{packageId}/manifest")
    @LcmsQuizBundleRead
    public ResponseEntity<ApiResponse<LcmsQuizPackageManifest>> manifest(
            @PathVariable String packageId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        LcmsQuizPackageManifest data = lcms.exportPackageManifest(access.tenantId(), packageId);
        return cached("Quiz package manifest returned", data, data.etag(), request);
    }

    @GetMapping("/api/v1/lcms/quiz-packages/{packageId}/download")
    @LcmsQuizBundleRead
    public ResponseEntity<Void> download(
            @PathVariable String packageId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        LcmsQuizPackageManifest manifest = lcms.exportPackageManifest(access.tenantId(), packageId);
        if (!manifest.portableReady() || manifest.downloadUrl() == null || manifest.downloadUrl().isBlank()) {
            throw new ResourceNotFoundException("portable package download is not ready");
        }
        CacheControl cache = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate().mustRevalidate();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(manifest.downloadUrl()))
                .cacheControl(cache)
                .eTag(quote(manifest.etag()))
                .build();
    }

    @GetMapping("/api/v1/lcms/quiz-packages/updates")
    @LcmsQuizBundleRead
    public ResponseEntity<ApiResponse<LcmsQuizPackageUpdateFeedResponse>> updates(
            @RequestParam String quizId,
            @RequestParam(required = false, defaultValue = "stable") String channel,
            @RequestParam(required = false) Integer currentVersion,
            @RequestParam(required = false) String runtimeVersion,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        LcmsQuizPackageUpdateFeedResponse data = lcms.packageUpdates(
                access.tenantId(), quizId, channel, currentVersion, runtimeVersion);
        return cached("Quiz package update feed returned", data, data.etag(), request);
    }

    @PostMapping("/api/v1/lcms/quiz-packages/{packageId}/revoke")
    @LcmsQuizPublish
    public ApiResponse<LcmsQuizBundleResponse.RevokeResponse> revoke(
            @PathVariable String packageId,
            @Valid @RequestBody LcmsQuizBundleRevokeRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz package revoked", lcms.revokeBundle(access.tenantId(), packageId, body.reason()), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private <T> ResponseEntity<ApiResponse<T>> cached(
            String message,
            T data,
            String etagValue,
            HttpServletRequest request
    ) {
        String etag = quote(etagValue);
        CacheControl cache = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate().mustRevalidate();
        if (matches(request.getHeader(HttpHeaders.IF_NONE_MATCH), etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).cacheControl(cache).eTag(etag).build();
        }
        return ResponseEntity.ok().cacheControl(cache).eTag(etag).body(ok(message, data, request));
    }

    private String quote(String value) {
        String safe = value == null || value.isBlank() ? "empty" : value.replace("\"", "");
        return "\"" + safe + "\"";
    }

    private boolean matches(String header, String etag) {
        if (header == null || header.isBlank()) return false;
        for (String candidate : header.split(",")) {
            String normalized = candidate.trim();
            if ("*".equals(normalized) || etag.equals(normalized)) return true;
        }
        return false;
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
