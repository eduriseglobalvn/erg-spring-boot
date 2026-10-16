package com.erg.ergspringboot.controller.lcms;

import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaCommitRequest;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaPresignRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaCommitResponse;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaPresignResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.security.ReviewedPublicEndpoint;
import com.erg.ergspringboot.lcms.LcmsOperations;
import com.erg.ergspringboot.lcms.authorization.LcmsMediaWrite;
import com.erg.ergspringboot.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * LCMS media pipeline (spec §4.3, §5): hash-first presign then commit. Replaces the FE's base64 data
 * URLs so slides reference committed, deduplicated, content-addressed assets by URL.
 */
@RestController
public class LcmsMediaController {

    private final LcmsOperations lcms;

    public LcmsMediaController(LcmsOperations lcms) {
        this.lcms = lcms;
    }

    @PutMapping("/api/v1/lcms/media/upload/quiz-media/{tenantId}/{prefix}/{filename}")
    @ReviewedPublicEndpoint
    public ResponseEntity<Void> uploadLocal(
            @PathVariable String tenantId,
            @PathVariable String prefix,
            @PathVariable String filename,
            @RequestHeader("Content-Type") String mimeType,
            @RequestBody byte[] bytes,
            HttpServletRequest request
    ) {
        if (bytes.length > lcms.maxMediaUploadBytes()) {
            throw new BadRequestException("media exceeds the maximum allowed size");
        }
        String uploadUrl = request.getRequestURL()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        if (!lcms.storeSignedMediaUpload(uploadUrl, tenantId, mimeType, bytes)) {
            throw new BadRequestException("signed media upload is invalid or expired");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/lcms/media/presign")
    @LcmsMediaWrite
    public ApiResponse<MediaPresignResponse> presign(
            @RequestBody MediaPresignRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Media presign resolved", lcms.presignMedia(access.tenantId(), access.userId(), body), request);
    }

    @PostMapping("/api/v1/lcms/media/{mediaId}/commit")
    @LcmsMediaWrite
    public ApiResponse<MediaCommitResponse> commit(
            @PathVariable String mediaId,
            @RequestBody(required = false) MediaCommitRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        MediaCommitRequest resolved = new MediaCommitRequest(
                mediaId,
                body == null ? null : body.width(),
                body == null ? null : body.height()
        );
        return ok("Media committed", lcms.commitMedia(access.tenantId(), resolved), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
