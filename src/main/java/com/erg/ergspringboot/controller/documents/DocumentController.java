package com.erg.ergspringboot.controller.documents;

import com.erg.ergspringboot.common.request.documents.DocumentRequests.UpdateDocumentRequest;
import com.erg.ergspringboot.common.request.documents.DocumentRequests.UploadDocumentRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.service.documents.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("prototype")
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> upload(@Valid @RequestBody(required = false) UploadDocumentRequest body, HttpServletRequest request) {
        if (body == null) {
            throw new BadRequestException("Request body is required for document upload");
        }
        return ok("Document uploaded", documentService.upload(body.toMap(), userId(), tenantId()), request);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        return ok("Documents loaded", documentService.list(), request);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id, HttpServletRequest request) {
        return ok("Document loaded", documentService.get(id), request);
    }

    @GetMapping("/{id}/file")
    public ApiResponse<Map<String, Object>> file(@PathVariable String id, HttpServletRequest request) {
        return ok("Document stream prepared", documentService.file(id), request);
    }

    @PatchMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id, @Valid @RequestBody UpdateDocumentRequest body, HttpServletRequest request) {
        if (body == null) {
            throw new BadRequestException("Request body is required for document update");
        }
        return ok("Document updated", documentService.update(id, body.toMap()), request);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id, HttpServletRequest request) {
        return ok("Document deleted", documentService.delete(id), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String userId() {
        var principal = SecurityUtils.principal();
        return principal != null ? principal.userId() : "system";
    }

    private String tenantId() {
        return SecurityUtils.normalizeTenant(SecurityUtils.principal() != null ? SecurityUtils.principal().tenantId() : null);
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
