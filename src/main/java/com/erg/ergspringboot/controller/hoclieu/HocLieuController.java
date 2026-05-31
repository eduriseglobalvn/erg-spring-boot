package com.erg.ergspringboot.controller.hoclieu;

import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.AssetRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.AttachResourceRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.ResourceRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.SlideAnnotationRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.TaxonomyNodeRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibraryBootstrapResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibraryProgressResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.ResourceListResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.SlideAnnotationResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.StudioAssetResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.StudioBootstrapResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.StudioResourceResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.StudioTaxonomyNodeResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.hoclieu.HocLieuService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HocLieuController {

    private final HocLieuService hocLieuService;

    public HocLieuController(HocLieuService hocLieuService) {
        this.hocLieuService = hocLieuService;
    }

    @GetMapping("/api/v1/hoclieu/home")
    public ApiResponse<Map<String, Object>> home(HttpServletRequest request) {
        return ok("HocLieu home loaded", hocLieuService.home(), request);
    }

    @GetMapping("/api/v1/hoclieu/library/bootstrap")
    public ApiResponse<LibraryBootstrapResponse> libraryBootstrap(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String academicYear,
            HttpServletRequest request
    ) {
        // Runtime library bootstrap returns a UI-ready tree scoped to the selected managed school.
        return ok("HocLieu library bootstrap loaded", hocLieuService.libraryBootstrap(schoolId, academicYear, UserAccessContext.from(request)), request);
    }

    @GetMapping("/api/v1/hoclieu/library/progress")
    public ApiResponse<LibraryProgressResponse> libraryProgress(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String academicYear,
            HttpServletRequest request
    ) {
        return ok("HocLieu library progress loaded", hocLieuService.libraryProgress(schoolId, academicYear, UserAccessContext.from(request)), request);
    }

    @GetMapping("/api/v1/admin/hoclieu/studio/bootstrap")
    public ApiResponse<StudioBootstrapResponse> studioBootstrap(HttpServletRequest request) {
        // Admin Studio bootstrap returns flat authoring data; it intentionally does not shape runtime UI trees.
        return ok("HocLieu Studio bootstrap loaded", hocLieuService.studioBootstrap(UserAccessContext.from(request)), request);
    }

    @GetMapping("/api/v1/hoclieu/resources")
    public ApiResponse<ResourceListResponse> resources(@RequestParam Map<String, String> query, HttpServletRequest request) {
        // Runtime resource reads must be filterable; callers should use subjectId or lessonId instead of global limits.
        return ok("HocLieu resources loaded", hocLieuService.resources(query, UserAccessContext.from(request)), request);
    }

    @GetMapping("/api/v1/admin/hoclieu/taxonomy")
    public ApiResponse<java.util.List<StudioTaxonomyNodeResponse>> taxonomyNodes(
            @RequestParam Map<String, String> query,
            HttpServletRequest request
    ) {
        return ok("HocLieu taxonomy loaded", hocLieuService.taxonomyNodes(query, UserAccessContext.from(request)), request);
    }

    @GetMapping("/api/v1/admin/hoclieu/taxonomy/{nodeId}")
    public ApiResponse<StudioTaxonomyNodeResponse> taxonomyNode(@PathVariable String nodeId, HttpServletRequest request) {
        return ok("HocLieu taxonomy node loaded", hocLieuService.taxonomyNode(nodeId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/api/v1/admin/hoclieu/taxonomy")
    public ApiResponse<StudioTaxonomyNodeResponse> createTaxonomy(
            @RequestBody TaxonomyNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu taxonomy node created", hocLieuService.createTaxonomy(body, UserAccessContext.from(request)), request);
    }

    @PostMapping("/api/v1/admin/hoclieu/taxonomy/{nodeId}/children")
    public ApiResponse<StudioTaxonomyNodeResponse> createChildTaxonomy(
            @PathVariable String nodeId,
            @RequestBody TaxonomyNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu taxonomy child created", hocLieuService.createChildTaxonomy(nodeId, body, UserAccessContext.from(request)), request);
    }

    @PatchMapping("/api/v1/admin/hoclieu/taxonomy/{nodeId}")
    public ApiResponse<StudioTaxonomyNodeResponse> updateTaxonomy(
            @PathVariable String nodeId,
            @RequestBody TaxonomyNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu taxonomy node updated", hocLieuService.updateTaxonomy(nodeId, body, UserAccessContext.from(request)), request);
    }

    @PutMapping("/api/v1/admin/hoclieu/taxonomy/{nodeId}")
    public ApiResponse<StudioTaxonomyNodeResponse> replaceTaxonomy(
            @PathVariable String nodeId,
            @RequestBody TaxonomyNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu taxonomy node updated", hocLieuService.updateTaxonomy(nodeId, body, UserAccessContext.from(request)), request);
    }

    @DeleteMapping("/api/v1/admin/hoclieu/taxonomy/{nodeId}")
    public ApiResponse<Map<String, Object>> deleteTaxonomy(@PathVariable String nodeId, HttpServletRequest request) {
        return ok("HocLieu taxonomy node deleted", hocLieuService.deleteTaxonomy(nodeId, UserAccessContext.from(request)), request);
    }

    @GetMapping({"/api/v1/hoclieu/resources/{resourceId}", "/api/v1/hoclieu/resources/{resourceId}/items"})
    public ApiResponse<Map<String, Object>> resource(@PathVariable String resourceId, HttpServletRequest request) {
        return ok("HocLieu resource loaded", hocLieuService.resourceDetail(resourceId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/api/v1/admin/hoclieu/resources")
    public ApiResponse<StudioResourceResponse> createResource(@RequestBody ResourceRequest body, HttpServletRequest request) {
        return ok("HocLieu resource created", hocLieuService.createResource(body, UserAccessContext.from(request)), request);
    }

    @PatchMapping("/api/v1/admin/hoclieu/resources/{resourceId}")
    public ApiResponse<StudioResourceResponse> updateResource(
            @PathVariable String resourceId,
            @RequestBody ResourceRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu resource updated", hocLieuService.updateResource(resourceId, body, UserAccessContext.from(request)), request);
    }

    @PutMapping("/api/v1/admin/hoclieu/resources/{resourceId}")
    public ApiResponse<StudioResourceResponse> replaceResource(
            @PathVariable String resourceId,
            @RequestBody ResourceRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu resource updated", hocLieuService.updateResource(resourceId, body, UserAccessContext.from(request)), request);
    }

    @DeleteMapping("/api/v1/admin/hoclieu/resources/{resourceId}")
    public ApiResponse<Map<String, Object>> deleteResource(@PathVariable String resourceId, HttpServletRequest request) {
        return ok("HocLieu resource deleted", hocLieuService.deleteResource(resourceId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/api/v1/admin/hoclieu/taxonomy/{nodeId}/resources")
    public ApiResponse<StudioResourceResponse> attachResource(
            @PathVariable String nodeId,
            @RequestBody(required = false) AttachResourceRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu resource attached", hocLieuService.attachResource(nodeId, body, UserAccessContext.from(request)), request);
    }

    @DeleteMapping("/api/v1/admin/hoclieu/taxonomy/{nodeId}/resources/{resourceId}")
    public ApiResponse<Map<String, Object>> detachResource(
            @PathVariable String nodeId,
            @PathVariable String resourceId,
            HttpServletRequest request
    ) {
        return ok("HocLieu resource detached", hocLieuService.detachResource(nodeId, resourceId, UserAccessContext.from(request)), request);
    }

    @PostMapping("/api/v1/admin/hoclieu/resources/{resourceId}/assets")
    public ApiResponse<StudioAssetResponse> createAsset(
            @PathVariable String resourceId,
            @RequestBody AssetRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu asset created", hocLieuService.createAsset(resourceId, body, UserAccessContext.from(request)), request);
    }

    @PatchMapping("/api/v1/admin/hoclieu/assets/{assetId}")
    public ApiResponse<StudioAssetResponse> updateAsset(
            @PathVariable String assetId,
            @RequestBody AssetRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu asset updated", hocLieuService.updateAsset(assetId, body, UserAccessContext.from(request)), request);
    }

    @PutMapping("/api/v1/admin/hoclieu/assets/{assetId}")
    public ApiResponse<StudioAssetResponse> replaceAsset(
            @PathVariable String assetId,
            @RequestBody AssetRequest body,
            HttpServletRequest request
    ) {
        return ok("HocLieu asset updated", hocLieuService.updateAsset(assetId, body, UserAccessContext.from(request)), request);
    }

    @DeleteMapping("/api/v1/admin/hoclieu/assets/{assetId}")
    public ApiResponse<Map<String, Object>> deleteAsset(@PathVariable String assetId, HttpServletRequest request) {
        return ok("HocLieu asset deleted", hocLieuService.deleteAsset(assetId, UserAccessContext.from(request)), request);
    }

    @GetMapping({
            "/api/v1/hoclieu/assets/{assetId}/launch",
            "/api/v1/hoclieu/assets/{assetId}/pages",
            "/api/v1/hoclieu/assets/{assetId}/stream",
            "/api/v1/hoclieu/assets/{assetId}/download"
    })
    public ApiResponse<Map<String, Object>> assetAccess(@PathVariable String assetId, HttpServletRequest request) {
        return ok("HocLieu asset access prepared", hocLieuService.launch(assetId, lastSegment(request.getRequestURI()), UserAccessContext.from(request)), request);
    }

    @GetMapping("/api/v1/hoclieu/assets/{assetId}/slides/{slideIndex}")
    public ResponseEntity<Void> slideImage(
            @PathVariable String assetId,
            @PathVariable int slideIndex,
            HttpServletRequest request
    ) {
        String location = hocLieuService.slideImageLocation(assetId, slideIndex, false, UserAccessContext.from(request));
        return cachedRedirect(location);
    }

    @GetMapping("/api/v1/hoclieu/assets/{assetId}/slides/{slideIndex}/thumbnail")
    public ResponseEntity<Void> slideThumbnail(
            @PathVariable String assetId,
            @PathVariable int slideIndex,
            HttpServletRequest request
    ) {
        String location = hocLieuService.slideImageLocation(assetId, slideIndex, true, UserAccessContext.from(request));
        return cachedRedirect(location);
    }

    @GetMapping("/api/v1/hoclieu/assets/{assetId}/slides/{slideIndex}/annotations")
    public ApiResponse<SlideAnnotationResponse> slideAnnotations(
            @PathVariable String assetId,
            @PathVariable int slideIndex,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String academicYear,
            HttpServletRequest request
    ) {
        return ok(
                "HocLieu slide annotations loaded",
                hocLieuService.slideAnnotations(assetId, slideIndex, schoolId, academicYear, UserAccessContext.from(request)),
                request
        );
    }

    @PutMapping("/api/v1/hoclieu/assets/{assetId}/slides/{slideIndex}/annotations")
    public ApiResponse<SlideAnnotationResponse> saveSlideAnnotations(
            @PathVariable String assetId,
            @PathVariable int slideIndex,
            @RequestBody(required = false) SlideAnnotationRequest body,
            HttpServletRequest request
    ) {
        return ok(
                "HocLieu slide annotations saved",
                hocLieuService.saveSlideAnnotations(assetId, slideIndex, body, UserAccessContext.from(request)),
                request
        );
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private ResponseEntity<Void> cachedRedirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location))
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .build();
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }

    private String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
