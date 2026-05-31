package com.erg.ergspringboot.service.hoclieu;

import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.AssetRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.AttachResourceRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.ResourceRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.SlideAnnotationRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.TaxonomyNodeRequest;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.DesignerPresetResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibraryBootstrapResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibraryGroupResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibraryLessonResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibraryLessonProgressResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibraryProgressResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibraryResourceResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.LibrarySubjectResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.ResourceListResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.SlideAnnotationResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.StudioAssetResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.StudioBootstrapResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.StudioResourceResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.StudioTaxonomyNodeResponse;
import com.erg.ergspringboot.common.response.hoclieu.HocLieuLibraryResponses.TaxonomyOptionResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.hoclieu.AssetSlideCache;
import com.erg.ergspringboot.model.hoclieu.AssetRecord;
import com.erg.ergspringboot.model.hoclieu.HocLieuProgressState;
import com.erg.ergspringboot.model.hoclieu.HocLieuSlideAnnotation;
import com.erg.ergspringboot.model.hoclieu.ResourceItemRecord;
import com.erg.ergspringboot.model.hoclieu.ResourceRecord;
import com.erg.ergspringboot.model.hoclieu.TaxonomyNode;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HocLieuService {

    private static final String DEFAULT_TENANT = "erg";
    private static final String ACTIVE = "active";
    private static final String PUBLISHED = "published";
    private static final String OPEN = "open";
    private static final Set<String> SUBJECT_KINDS = Set.of("subject", "subjects");
    private static final Set<String> GROUP_KINDS = Set.of("category", "categories", "group", "groups");
    private static final Set<String> LESSON_KINDS = Set.of("section", "sections", "lesson", "lessons");
    private static final List<String> FILE_TYPES = List.of("PDF", "PPTX", "VIDEO", "AUDIO", "HTML5", "LINK", "QUIZ", "ZIP", "DOCX", "XLSX", "IMAGE");

    private final HocLieuTaxonomyService taxonomyService;
    private final HocLieuResourceService resourceService;
    private final HocLieuAssetService assetService;
    private final HocLieuProgressService progressService;

    public HocLieuService(
            HocLieuTaxonomyService taxonomyService,
            HocLieuResourceService resourceService,
            HocLieuAssetService assetService,
            HocLieuProgressService progressService
    ) {
        this.taxonomyService = taxonomyService;
        this.resourceService = resourceService;
        this.assetService = assetService;
        this.progressService = progressService;
    }

    public Map<String, Object> home() {
        return Map.of(
                "hero", Map.of("title", "HocLieu", "subtitle", "Digital learning resources"),
                "shortcuts", List.of("library", "resources", "teacher"),
                "metrics", Map.of("bootstrap", "/api/v1/hoclieu/library/bootstrap")
        );
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    @Cacheable(
            cacheNames = "hoclieuLibraryBootstrap",
            key = "{#access.tenantId(), #schoolId, #academicYear, #access.currentEducationUnitId(), #access.managedEducationUnitIds().toString()}"
    )
    public LibraryBootstrapResponse libraryBootstrap(String schoolId, String academicYear, UserAccessContext access) {
        String resolvedSchoolId = requiredSchoolId(schoolId, access);
        String resolvedAcademicYear = academicYear == null || academicYear.isBlank() ? currentAcademicYear() : academicYear.trim();

        // Guard the read model at service level so FE cannot switch schoolId outside the logged-in user's managed scope.
        ensureSchoolAccess(resolvedSchoolId, access);

        String tenantId = normalizeTenant(access.tenantId());
        List<TaxonomyNode> taxonomy = activeTaxonomy(tenantId);
        List<ResourceRecord> resources = resourceService.findScoped(tenantId, null, null, null, null, null, null).stream()
                .filter(this::isRuntimeVisibleResource)
                .toList();
        ResourceLookup lookup = resourceLookup(tenantId, resources);

        // Assemble the exact tree the HocLieu library screen renders: subject -> group -> lesson -> resources.
        List<LibrarySubjectResponse> subjects = taxonomy.stream()
                .filter(this::isSubject)
                .sorted(taxonomyOrder())
                .map(subject -> toLibrarySubject(subject, taxonomy, resources, lookup))
                .filter(subject -> !subject.groups().isEmpty())
                .toList();

        return new LibraryBootstrapResponse(resolvedSchoolId, resolvedAcademicYear, subjects);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public LibraryProgressResponse libraryProgress(String schoolId, String academicYear, UserAccessContext access) {
        String resolvedSchoolId = requiredSchoolId(schoolId, access);
        String resolvedAcademicYear = academicYear == null || academicYear.isBlank() ? currentAcademicYear() : academicYear.trim();
        ensureSchoolAccess(resolvedSchoolId, access);

        String tenantId = normalizeTenant(access.tenantId());
        List<TaxonomyNode> taxonomy = activeTaxonomy(tenantId);
        Map<String, Integer> progressByLessonId = progressByLessonId(tenantId, access.userId(), resolvedSchoolId, resolvedAcademicYear, taxonomy);
        List<LibraryLessonProgressResponse> lessons = progressByLessonId.entrySet().stream()
                .map(entry -> new LibraryLessonProgressResponse(entry.getKey(), entry.getValue()))
                .toList();
        return new LibraryProgressResponse(resolvedSchoolId, resolvedAcademicYear, lessons);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    @Cacheable(cacheNames = "hoclieuStudioBootstrap", key = "#access.tenantId()")
    public StudioBootstrapResponse studioBootstrap(UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        List<TaxonomyNode> taxonomy = activeTaxonomy(tenantId);
        List<ResourceRecord> resources = resourceService.findScoped(tenantId, null, null, null, null, null, null);

        // Admin Studio needs flat authoring buckets, while runtime gets a nested tree.
        return new StudioBootstrapResponse(
                taxonomy.stream().filter(this::isSubject).sorted(taxonomyOrder()).map(this::toTaxonomyOption).toList(),
                taxonomy.stream().filter(this::isGroup).sorted(taxonomyOrder()).map(this::toTaxonomyOption).toList(),
                taxonomy.stream().filter(this::isLesson).sorted(taxonomyOrder()).map(this::toTaxonomyOption).toList(),
                resources.stream().map(this::toStudioResource).toList(),
                FILE_TYPES,
                designerPresets()
        );
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    @Cacheable(
            cacheNames = "hoclieuResources",
            key = "{#access.tenantId(), #query.toString(), #access.currentEducationUnitId(), #access.managedEducationUnitIds().toString()}"
    )
    public ResourceListResponse resources(Map<String, String> query, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        List<ResourceRecord> resources = resourceService.findScoped(
                tenantId,
                blankToNull(query.get("subjectId")),
                blankToNull(query.get("categoryId")),
                blankToNull(query.get("sectionId")),
                blankToNull(query.get("lessonId")),
                lowerOrNull(query.get("status")),
                lowerOrNull(query.get("visibility"))
        );
        return new ResourceListResponse(resources.stream().map(this::toStudioResource).toList(), scopePayload(access));
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<StudioTaxonomyNodeResponse> taxonomyNodes(Map<String, String> query, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        String parentId = blankToNull(query.get("parentId"));
        String kind = canonicalKind(blankToNull(query.get("kind")));
        List<TaxonomyNode> nodes = parentId != null
                ? taxonomyService.findByTenantIdAndParentIdAndStatusIgnoreCaseOrderBySortOrderAscLabelAsc(tenantId, parentId, ACTIVE)
                : kind == null
                ? activeTaxonomy(tenantId)
                : taxonomyService.findByTenantIdAndKindIgnoreCaseAndStatusIgnoreCaseOrderBySortOrderAscLabelAsc(tenantId, kind, ACTIVE);
        return nodes.stream().map(node -> toStudioTaxonomyNode(node, tenantId)).toList();
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public StudioTaxonomyNodeResponse taxonomyNode(String nodeId, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        return toStudioTaxonomyNode(requireTaxonomyNode(nodeId, tenantId), tenantId);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    @Cacheable(cacheNames = "hoclieuResourceDetail", key = "{#access.tenantId(), #resourceId}")
    public Map<String, Object> resourceDetail(String resourceId, UserAccessContext access) {
        ResourceRecord resource = resourceService.findById(resourceId)
                .filter(item -> normalizeTenant(access.tenantId()).equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"));
        List<AssetRecord> assets = assetService.findByResourceIdOrderByUpdatedAtDesc(resource.getId());
        List<ResourceItemRecord> items = resourceService.findItemsByTenantIdAndResourceIdOrderBySortOrderAscUnitTitleAscLessonTitleAsc(
                normalizeTenant(access.tenantId()),
                resource.getId()
        );
        return resourcePayload(resource, assets, items);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioTaxonomyNodeResponse createTaxonomy(TaxonomyNodeRequest request, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        TaxonomyLink link = resolveTaxonomyLink(request, tenantId);
        TaxonomyNode node = new TaxonomyNode();
        node.setId(IdGenerator.newId());
        applyTaxonomyRequest(node, request, link);
        node.setTenantId(tenantId);
        return toStudioTaxonomyNode(taxonomyService.save(node), tenantId);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioTaxonomyNodeResponse createChildTaxonomy(String parentId, TaxonomyNodeRequest request, UserAccessContext access) {
        TaxonomyNodeRequest body = new TaxonomyNodeRequest(
                request == null ? null : request.kind(),
                request == null ? null : request.label(),
                request == null ? null : request.slug(),
                parentId,
                request == null ? null : request.subjectId(),
                request == null ? null : request.categoryId(),
                request == null ? null : request.sortOrder(),
                request == null ? null : request.status(),
                request == null ? null : request.description()
        );
        return createTaxonomy(body, access);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioTaxonomyNodeResponse updateTaxonomy(String nodeId, TaxonomyNodeRequest request, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        TaxonomyNode node = requireTaxonomyNode(nodeId, tenantId);
        TaxonomyNodeRequest merged = mergeTaxonomyRequest(node, request);
        TaxonomyLink link = resolveTaxonomyLink(merged, tenantId);
        applyTaxonomyRequest(node, merged, link);
        return toStudioTaxonomyNode(taxonomyService.save(node), tenantId);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public Map<String, Object> deleteTaxonomy(String nodeId, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        TaxonomyNode node = requireTaxonomyNode(nodeId, tenantId);
        long childCount = taxonomyService.countByTenantIdAndParentIdAndStatusIgnoreCase(tenantId, nodeId, ACTIVE);
        long resourceCount = resourceCount(node, tenantId);
        if (childCount > 0 || resourceCount > 0) {
            throw new BadRequestException("taxonomy node still has children or resources");
        }
        node.setStatus("deleted");
        taxonomyService.save(node);
        return Map.of("deleted", true, "id", nodeId);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioResourceResponse createResource(Map<String, Object> body, UserAccessContext access) {
        return createResource(toResourceRequest(body), access);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioResourceResponse createResource(ResourceRequest body, UserAccessContext access) {
        ResourceRecord resource = new ResourceRecord();
        resource.setId(IdGenerator.newId());
        resource.setTenantId(normalizeTenant(access.tenantId()));
        applyResourceRequest(resource, body, access, true);
        resource.setCreatedBy(access.userId());
        resource.setUpdatedBy(access.userId());
        resource.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toStudioResource(resourceService.save(resource));
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioResourceResponse updateResource(String resourceId, Map<String, Object> body, UserAccessContext access) {
        return updateResource(resourceId, toResourceRequest(body), access);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioResourceResponse updateResource(String resourceId, ResourceRequest body, UserAccessContext access) {
        ResourceRecord resource = resourceService.findById(resourceId)
                .filter(item -> normalizeTenant(access.tenantId()).equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"));

        applyResourceRequest(resource, body, access, false);
        resource.setUpdatedBy(access.userId());
        return toStudioResource(resourceService.save(resource));
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public Map<String, Object> deleteResource(String resourceId, UserAccessContext access) {
        ResourceRecord resource = resourceService.findById(resourceId)
                .filter(item -> normalizeTenant(access.tenantId()).equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"));
        assetService.deleteByResourceId(resource.getId());
        resourceService.deleteItemsByResourceId(resource.getId());
        resourceService.delete(resource);
        return Map.of("deleted", true, "id", resourceId);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioResourceResponse attachResource(String nodeId, AttachResourceRequest request, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        TaxonomyNode node = requireTaxonomyNode(nodeId, tenantId);
        ResourceRecord resource = request != null && request.resourceId() != null && !request.resourceId().isBlank()
                ? resourceService.findById(request.resourceId())
                .filter(item -> tenantId.equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"))
                : newResourceForNode(node, request, access, tenantId);
        applyNodePlacement(resource, node);
        resource.setUpdatedBy(access.userId());
        return toStudioResource(resourceService.save(resource));
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public Map<String, Object> detachResource(String nodeId, String resourceId, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        requireTaxonomyNode(nodeId, tenantId);
        ResourceRecord resource = resourceService.findById(resourceId)
                .filter(item -> tenantId.equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"));
        if (nodeId.equals(resource.getSectionId()) || nodeId.equals(resource.getTopicId())) {
            resource.setSectionId("");
            resource.setTopicId("");
        } else if (nodeId.equals(resource.getCategoryId())) {
            resource.setCategoryId("");
            resource.setSectionId("");
            resource.setTopicId("");
        } else if (nodeId.equals(resource.getSubjectId())) {
            resource.setSubjectId("");
            resource.setCategoryId("");
            resource.setSectionId("");
            resource.setTopicId("");
        }
        resource.setUpdatedBy(access.userId());
        resourceService.save(resource);
        return Map.of("detached", true, "resourceId", resourceId, "nodeId", nodeId);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioAssetResponse createAsset(String resourceId, AssetRequest request, UserAccessContext access) {
        ResourceRecord resource = resourceService.findById(resourceId)
                .filter(item -> normalizeTenant(access.tenantId()).equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"));
        AssetRecord asset = new AssetRecord();
        asset.setId(IdGenerator.newId());
        asset.setResourceId(resource.getId());
        applyAssetRequest(asset, request, true);
        AssetRecord saved = assetService.save(asset);
        syncSlideCacheFromMetadata(saved, resource.getTenantId(), request == null ? null : request.metadata());
        return toStudioAsset(saved);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public StudioAssetResponse updateAsset(String assetId, AssetRequest request, UserAccessContext access) {
        AssetRecord asset = assetService.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu asset not found"));
        ResourceRecord resource = resourceService.findById(asset.getResourceId())
                .filter(item -> normalizeTenant(access.tenantId()).equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"));
        applyAssetRequest(asset, request, false);
        AssetRecord saved = assetService.save(asset);
        syncSlideCacheFromMetadata(saved, resource.getTenantId(), request == null ? null : request.metadata());
        return toStudioAsset(saved);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"hoclieuLibraryBootstrap", "hoclieuStudioBootstrap", "hoclieuResources", "hoclieuResourceDetail"}, allEntries = true)
    public Map<String, Object> deleteAsset(String assetId, UserAccessContext access) {
        AssetRecord asset = assetService.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu asset not found"));
        resourceService.findById(asset.getResourceId())
                .filter(item -> normalizeTenant(access.tenantId()).equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"));
        assetService.deleteSlidesByAssetId(asset.getId());
        assetService.delete(asset);
        return Map.of("deleted", true, "id", assetId);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public Map<String, Object> launch(String assetId, String mode, UserAccessContext access) {
        AssetContext context = requireAssetContext(assetId, access);
        AssetRecord asset = context.asset();
        List<AssetSlideCache> slides = assetService.findSlides(context.tenantId(), asset.getId(), assetSourceVersion(asset), "ready");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetId", asset.getId());
        payload.put("resourceId", asset.getResourceId());
        payload.put("mode", mode);
        payload.put("title", firstNonBlank(asset.getTitle(), context.resource().getTitle()));
        payload.put("fileType", firstNonBlank(asset.getSelectedFileType(), context.resource().getDocumentTypeId(), ""));
        payload.put("launchMode", slides.isEmpty() ? fallbackLaunchMode(asset) : "custom_slide_viewer");
        payload.put("slideCount", slides.isEmpty() ? Optional.ofNullable(manualSlideCount(asset)).orElse(0) : slides.size());
        payload.put("slides", slides.stream().map(this::slidePayload).toList());
        payload.put("canDownload", Boolean.TRUE.equals(asset.getCanDownload()));
        payload.put("embedUrl", asset.getStorageUrl());
        payload.put("url", slides.isEmpty() ? assetLaunchUrl(asset, mode) : "/api/v1/hoclieu/assets/" + asset.getId() + "/launch");
        payload.put("expiresAt", OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15).toString());
        if (slides.isEmpty()) {
            payload.put("fallbackReason", "slide render cache is not ready");
        }
        return payload;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public String slideImageLocation(String assetId, int slideIndex, boolean thumbnail, UserAccessContext access) {
        AssetContext context = requireAssetContext(assetId, access);
        AssetSlideCache slide = assetService
                .findSlide(context.tenantId(), assetId, assetSourceVersion(context.asset()), slideIndex, "ready")
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu slide image not found"));
        String location = thumbnail ? slide.getThumbnailUrl() : slide.getImageUrl();
        if (location == null || location.isBlank()) {
            throw new ResourceNotFoundException("HocLieu slide image not ready");
        }
        return location;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public SlideAnnotationResponse slideAnnotations(
            String assetId,
            int slideIndex,
            String schoolId,
            String academicYear,
            UserAccessContext access
    ) {
        requireAssetContext(assetId, access);
        String resolvedSchoolId = requiredSchoolId(schoolId, access);
        ensureSchoolAccess(resolvedSchoolId, access);
        String resolvedAcademicYear = firstNonBlank(academicYear, currentAcademicYear());
        List<Map<String, Object>> paths = progressService
                .findFirstAnnotation(
                        normalizeTenant(access.tenantId()),
                        access.userId(),
                        resolvedSchoolId,
                        resolvedAcademicYear,
                        assetId,
                        requireSlideIndex(slideIndex)
                )
                .map(HocLieuSlideAnnotation::getPaths)
                .orElse(List.of());
        return new SlideAnnotationResponse(assetId, slideIndex, resolvedSchoolId, resolvedAcademicYear, paths);
    }

    @Transactional("postgresTransactionManager")
    public SlideAnnotationResponse saveSlideAnnotations(
            String assetId,
            int slideIndex,
            SlideAnnotationRequest request,
            UserAccessContext access
    ) {
        requireAssetContext(assetId, access);
        String resolvedSchoolId = requiredSchoolId(request == null ? null : request.schoolId(), access);
        ensureSchoolAccess(resolvedSchoolId, access);
        String resolvedAcademicYear = firstNonBlank(request == null ? null : request.academicYear(), currentAcademicYear());
        int resolvedSlideIndex = requireSlideIndex(slideIndex);
        String tenantId = normalizeTenant(access.tenantId());
        HocLieuSlideAnnotation annotation = progressService
                .findFirstAnnotation(tenantId, access.userId(), resolvedSchoolId, resolvedAcademicYear, assetId, resolvedSlideIndex)
                .orElseGet(() -> newAnnotation(tenantId, access.userId(), resolvedSchoolId, resolvedAcademicYear, assetId, resolvedSlideIndex));
        annotation.setPaths(request == null || request.paths() == null ? List.of() : request.paths());
        progressService.saveAnnotation(annotation);
        return new SlideAnnotationResponse(assetId, resolvedSlideIndex, resolvedSchoolId, resolvedAcademicYear, annotation.getPaths());
    }

    private LibrarySubjectResponse toLibrarySubject(
            TaxonomyNode subject,
            List<TaxonomyNode> taxonomy,
            List<ResourceRecord> resources,
            ResourceLookup lookup
    ) {
        List<LibraryGroupResponse> groups = taxonomy.stream()
                .filter(this::isGroup)
                .filter(group -> belongsToSubject(group, subject.getId()))
                .sorted(taxonomyOrder())
                .map(group -> toLibraryGroup(subject, group, taxonomy, resources, lookup))
                .toList();
        return new LibrarySubjectResponse(subject.getId(), subject.getLabel(), groups);
    }

    private LibraryGroupResponse toLibraryGroup(
            TaxonomyNode subject,
            TaxonomyNode group,
            List<TaxonomyNode> taxonomy,
            List<ResourceRecord> resources,
            ResourceLookup lookup
    ) {
        List<LibraryLessonResponse> lessons = taxonomy.stream()
                .filter(this::isLesson)
                .filter(lesson -> Objects.equals(lesson.getCategoryId(), group.getId()) || Objects.equals(lesson.getParentId(), group.getId()))
                .sorted(taxonomyOrder())
                .map(lesson -> toLibraryLesson(subject, group, lesson, resources, lookup))
                .toList();
        if (lessons.isEmpty()) {
            lessons = syntheticLessonsFromResources(subject, group, resources, lookup);
        }
        return new LibraryGroupResponse(group.getId(), group.getLabel(), lessons);
    }

    private LibraryLessonResponse toLibraryLesson(
            TaxonomyNode subject,
            TaxonomyNode group,
            TaxonomyNode lesson,
            List<ResourceRecord> resources,
            ResourceLookup lookup
    ) {
        List<LibraryResourceResponse> lessonResources = resources.stream()
                .filter(resource -> belongsToSubject(resource, subject.getId()))
                .filter(resource -> resourceBelongsToLibraryGroup(resource, group, lookup))
                .filter(resource -> resourceBelongsToLibraryLesson(resource, group, lesson, lookup))
                .map(resource -> toLibraryResource(resource, lookup, group, lesson))
                .toList();

        return new LibraryLessonResponse(lesson.getId(), lesson.getLabel(), lessonResources);
    }

    private List<LibraryLessonResponse> syntheticLessonsFromResources(
            TaxonomyNode subject,
            TaxonomyNode group,
            List<ResourceRecord> resources,
            ResourceLookup lookup
    ) {
        List<LibraryResourceResponse> groupResources = resources.stream()
                .filter(resource -> belongsToSubject(resource, subject.getId()))
                .filter(resource -> Objects.equals(resource.getCategoryId(), group.getId()))
                .map(resource -> toLibraryResource(resource, lookup.assetsByResourceId().getOrDefault(resource.getId(), List.of())))
                .toList();
        if (groupResources.isEmpty()) {
            return List.of();
        }
        return List.of(new LibraryLessonResponse(group.getId(), group.getLabel(), groupResources));
    }

    private boolean resourceBelongsToLibraryGroup(ResourceRecord resource, TaxonomyNode group, ResourceLookup lookup) {
        if (Objects.equals(resource.getCategoryId(), group.getId())) {
            return true;
        }
        return lookup.itemsByResourceId().getOrDefault(resource.getId(), List.of()).stream()
                .anyMatch(item -> itemTargetsGroup(item, group));
    }

    private boolean resourceBelongsToLibraryLesson(ResourceRecord resource, TaxonomyNode group, TaxonomyNode lesson, ResourceLookup lookup) {
        if (Objects.equals(resource.getSectionId(), lesson.getId()) || Objects.equals(resource.getTopicId(), lesson.getId())) {
            return true;
        }
        return matchingResourceItems(resource, lookup, group, lesson).findAny().isPresent();
    }

    private java.util.stream.Stream<ResourceItemRecord> matchingResourceItems(
            ResourceRecord resource,
            ResourceLookup lookup,
            TaxonomyNode group,
            TaxonomyNode lesson
    ) {
        return lookup.itemsByResourceId().getOrDefault(resource.getId(), List.of()).stream()
                .filter(item -> itemTargetsGroup(item, group))
                .filter(item -> itemTargetsLesson(item, lesson));
    }

    private boolean itemTargetsGroup(ResourceItemRecord item, TaxonomyNode group) {
        String unit = lowerOrNull(item.getUnitTitle());
        return unit == null || Objects.equals(unit, lowerOrNull(group.getId())) || Objects.equals(unit, lowerOrNull(group.getLabel()));
    }

    private boolean itemTargetsLesson(ResourceItemRecord item, TaxonomyNode lesson) {
        String lessonTitle = lowerOrNull(item.getLessonTitle());
        return lessonTitle != null
                && (Objects.equals(lessonTitle, lowerOrNull(lesson.getId())) || Objects.equals(lessonTitle, lowerOrNull(lesson.getLabel())));
    }

    private ResourceLookup resourceLookup(String tenantId, List<ResourceRecord> resources) {
        List<String> resourceIds = resources.stream().map(ResourceRecord::getId).toList();
        if (resourceIds.isEmpty()) {
            return new ResourceLookup(Map.of(), Map.of());
        }
        Map<String, List<AssetRecord>> assets = assetService.findByResourceIdInOrderByUpdatedAtDesc(resourceIds).stream()
                .collect(Collectors.groupingBy(AssetRecord::getResourceId, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ResourceItemRecord>> items = resourceService
                .findItemsByTenantIdAndResourceIdInOrderBySortOrderAscUnitTitleAscLessonTitleAsc(tenantId, resourceIds)
                .stream()
                .collect(Collectors.groupingBy(ResourceItemRecord::getResourceId, LinkedHashMap::new, Collectors.toList()));
        return new ResourceLookup(assets, items);
    }

    private Map<String, Integer> progressByLessonId(
            String tenantId,
            String userId,
            String schoolId,
            String academicYear,
            List<TaxonomyNode> taxonomy
    ) {
        List<String> nodeIds = taxonomy.stream()
                .filter(node -> isGroup(node) || isLesson(node))
                .map(TaxonomyNode::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (nodeIds.isEmpty()) {
            return Map.of();
        }
        return progressService
                .findByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndLessonIdIn(tenantId, userId, schoolId, academicYear, nodeIds)
                .stream()
                .collect(Collectors.groupingBy(
                        HocLieuProgressState::getLessonId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(this::progressStateTimestamp)),
                                state -> state.map(HocLieuProgressState::getProgressRate).orElse(0)
                        )
                ));
    }

    private LibraryResourceResponse toLibraryResource(ResourceRecord resource, List<AssetRecord> assets) {
        AssetRecord primaryAsset = assets.isEmpty() ? null : assets.getFirst();
        String fileType = primaryAsset == null ? resource.getDocumentTypeId() : primaryAsset.getSelectedFileType();
        return new LibraryResourceResponse(
                resource.getId(),
                resource.getTitle(),
                resource.getDocumentTypeId(),
                fileType,
                resource.getThumbnailUrl(),
                primaryAsset == null ? null : assetLaunchUrl(primaryAsset, "launch")
        );
    }

    private LibraryResourceResponse toLibraryResource(ResourceRecord resource, ResourceLookup lookup, TaxonomyNode group, TaxonomyNode lesson) {
        List<AssetRecord> assets = lookup.assetsByResourceId().getOrDefault(resource.getId(), List.of());
        Set<String> preferredAssetIds = matchingResourceItems(resource, lookup, group, lesson)
                .map(ResourceItemRecord::getAssetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (preferredAssetIds.isEmpty() || assets.size() < 2) {
            return toLibraryResource(resource, assets);
        }
        List<AssetRecord> orderedAssets = new ArrayList<>(assets);
        orderedAssets.sort(Comparator.comparing(asset -> preferredAssetIds.contains(asset.getId()) ? 0 : 1));
        return toLibraryResource(resource, orderedAssets);
    }

    private Map<String, Object> slidePayload(AssetSlideCache slide) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", slide.getId());
        payload.put("index", slide.getSlideIndex());
        payload.put("title", firstNonBlank(slide.getTitle(), "Slide " + slide.getSlideIndex()));
        payload.put("imageUrl", "/api/v1/hoclieu/assets/" + slide.getAssetId() + "/slides/" + slide.getSlideIndex());
        payload.put("thumbnailUrl", "/api/v1/hoclieu/assets/" + slide.getAssetId() + "/slides/" + slide.getSlideIndex() + "/thumbnail");
        return payload;
    }

    private Integer manualSlideCount(AssetRecord asset) {
        Map<String, Object> manifest = asset.getGoogleSlideManifest();
        if (manifest == null || manifest.isEmpty()) {
            return null;
        }
        Integer totalSlides = objectInteger(manifest.get("totalSlides"), 0);
        if (totalSlides == null || totalSlides <= 0) {
            totalSlides = objectInteger(manifest.get("slideCount"), 0);
        }
        return totalSlides != null && totalSlides > 0 ? totalSlides : null;
    }

    private OffsetDateTime progressStateTimestamp(HocLieuProgressState state) {
        return Optional.ofNullable(state.getUpdatedAt())
                .or(() -> Optional.ofNullable(state.getCreatedAt()))
                .orElse(OffsetDateTime.MIN);
    }

    private StudioResourceResponse toStudioResource(ResourceRecord resource) {
        return new StudioResourceResponse(
                resource.getId(),
                resource.getTitle(),
                resource.getSubjectId(),
                resource.getCategoryId(),
                firstNonBlank(resource.getSectionId(), resource.getTopicId()),
                resource.getDocumentTypeId(),
                resource.getStatus(),
                resource.getVisibility(),
                resource.getUpdatedAt()
        );
    }

    private TaxonomyOptionResponse toTaxonomyOption(TaxonomyNode node) {
        return new TaxonomyOptionResponse(
                node.getId(),
                node.getLabel(),
                node.getSlug(),
                node.getParentId(),
                node.getSubjectId(),
                node.getCategoryId(),
                Optional.ofNullable(node.getSortOrder()).orElse(0),
                node.getStatus()
        );
    }

    private StudioTaxonomyNodeResponse toStudioTaxonomyNode(TaxonomyNode node, String tenantId) {
        return new StudioTaxonomyNodeResponse(
                node.getId(),
                canonicalKind(node.getKind()),
                node.getLabel(),
                node.getSlug(),
                node.getParentId(),
                node.getSubjectId(),
                node.getCategoryId(),
                Optional.ofNullable(node.getSortOrder()).orElse(0),
                node.getStatus(),
                node.getDescription(),
                taxonomyService.countByTenantIdAndParentIdAndStatusIgnoreCase(tenantId, node.getId(), ACTIVE),
                resourceCount(node, tenantId)
        );
    }

    private StudioAssetResponse toStudioAsset(AssetRecord asset) {
        return new StudioAssetResponse(
                asset.getId(),
                asset.getResourceId(),
                asset.getTitle(),
                asset.getSelectedFileType(),
                asset.getLaunchMode(),
                asset.getOriginalFileName(),
                asset.getDetectedMimeType(),
                asset.getFileExtension(),
                asset.getFileSizeBytes(),
                asset.getStorageProvider(),
                asset.getStorageUrl(),
                Boolean.TRUE.equals(asset.getCanDownload()),
                asset.getStatus()
        );
    }

    private String assetLaunchUrl(AssetRecord asset, String mode) {
        return "/api/v1/hoclieu/assets/" + asset.getId() + "/" + mode;
    }

    private Map<String, Object> resourcePayload(ResourceRecord resource, List<AssetRecord> assets, List<ResourceItemRecord> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", resource.getId());
        payload.put("title", resource.getTitle());
        payload.put("subjectId", resource.getSubjectId());
        payload.put("categoryId", resource.getCategoryId());
        payload.put("lessonId", firstNonBlank(resource.getSectionId(), resource.getTopicId()));
        payload.put("thumbnailUrl", resource.getThumbnailUrl());
        payload.put("status", resource.getStatus());
        payload.put("visibility", resource.getVisibility());
        payload.put("assets", assets.stream().map(this::assetPayload).toList());
        payload.put("items", items.stream().map(this::itemPayload).toList());
        return payload;
    }

    private Map<String, Object> assetPayload(AssetRecord asset) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", asset.getId());
        payload.put("resourceId", asset.getResourceId());
        payload.put("title", asset.getTitle());
        payload.put("fileType", Optional.ofNullable(asset.getSelectedFileType()).orElse(""));
        payload.put("launchMode", Optional.ofNullable(asset.getLaunchMode()).orElse("download_only"));
        payload.put("storageUrl", asset.getStorageUrl());
        payload.put("url", assetLaunchUrl(asset, "launch"));
        payload.put("canDownload", Boolean.TRUE.equals(asset.getCanDownload()));
        return payload;
    }

    private Map<String, Object> itemPayload(ResourceItemRecord item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("resourceId", item.getResourceId());
        payload.put("assetId", item.getAssetId());
        payload.put("unitTitle", item.getUnitTitle());
        payload.put("lessonTitle", item.getLessonTitle());
        payload.put("sortOrder", Optional.ofNullable(item.getSortOrder()).orElse(0));
        return payload;
    }

    private List<TaxonomyNode> activeTaxonomy(String tenantId) {
        return taxonomyService.findByTenantIdAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc(tenantId, ACTIVE);
    }

    private TaxonomyNode requireTaxonomyNode(String nodeId, String tenantId) {
        return taxonomyService.findById(nodeId)
                .filter(node -> tenantId.equals(node.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu taxonomy node not found"));
    }

    private TaxonomyLink resolveTaxonomyLink(TaxonomyNodeRequest request, String tenantId) {
        if (request == null) {
            throw new BadRequestException("taxonomy payload is required");
        }
        TaxonomyNode parent = request.parentId() == null || request.parentId().isBlank()
                ? null
                : requireTaxonomyNode(request.parentId(), tenantId);
        String kind = canonicalKind(firstNonBlank(request.kind(), inferredChildKind(parent)));
        if (kind == null) {
            throw new BadRequestException("kind is required");
        }
        if ("subject".equals(kind)) {
            return new TaxonomyLink(kind, null, null, null, 0);
        }
        if ("category".equals(kind)) {
            String subjectId = parent != null && isSubject(parent) ? parent.getId() : request.subjectId();
            if (subjectId == null || subjectId.isBlank()) {
                throw new BadRequestException("subjectId is required for category");
            }
            return new TaxonomyLink(kind, parent == null ? null : parent.getId(), subjectId, null, 1);
        }
        if ("section".equals(kind)) {
            String categoryId = parent != null && isGroup(parent) ? parent.getId() : request.categoryId();
            if (categoryId == null || categoryId.isBlank()) {
                throw new BadRequestException("categoryId is required for lesson");
            }
            TaxonomyNode category = requireTaxonomyNode(categoryId, tenantId);
            String subjectId = firstNonBlank(request.subjectId(), category.getSubjectId());
            return new TaxonomyLink(kind, parent == null ? categoryId : parent.getId(), subjectId, categoryId, 2);
        }
        throw new BadRequestException("unsupported taxonomy kind");
    }

    private TaxonomyNodeRequest mergeTaxonomyRequest(TaxonomyNode node, TaxonomyNodeRequest request) {
        if (request == null) {
            return new TaxonomyNodeRequest(node.getKind(), node.getLabel(), node.getSlug(), node.getParentId(), node.getSubjectId(),
                    node.getCategoryId(), node.getSortOrder(), node.getStatus(), node.getDescription());
        }
        return new TaxonomyNodeRequest(
                firstNonBlank(request.kind(), node.getKind()),
                firstNonBlank(request.label(), node.getLabel()),
                firstNonBlank(request.slug(), node.getSlug()),
                firstNonBlank(request.parentId(), node.getParentId()),
                firstNonBlank(request.subjectId(), node.getSubjectId()),
                firstNonBlank(request.categoryId(), node.getCategoryId()),
                request.sortOrder() == null ? node.getSortOrder() : request.sortOrder(),
                firstNonBlank(request.status(), node.getStatus()),
                request.description() == null ? node.getDescription() : request.description()
        );
    }

    private void applyTaxonomyRequest(TaxonomyNode node, TaxonomyNodeRequest request, TaxonomyLink link) {
        String label = firstNonBlank(request.label(), node.getLabel());
        if (label == null) {
            throw new BadRequestException("label is required");
        }
        node.setKind(link.kind());
        node.setLabel(label);
        node.setSlug(firstNonBlank(request.slug(), node.getSlug(), slug(label)));
        node.setParentId(link.parentId());
        node.setSubjectId(link.subjectId());
        node.setCategoryId(link.categoryId());
        node.setDepth(link.depth());
        node.setSortOrder(request.sortOrder() == null ? Optional.ofNullable(node.getSortOrder()).orElse(0) : request.sortOrder());
        node.setStatus(firstNonBlank(request.status(), node.getStatus(), ACTIVE));
        node.setDescription(request.description() == null ? node.getDescription() : request.description());
    }

    private String inferredChildKind(TaxonomyNode parent) {
        if (parent == null) {
            return "subject";
        }
        if (isSubject(parent)) {
            return "category";
        }
        if (isGroup(parent)) {
            return "section";
        }
        throw new BadRequestException("lesson nodes cannot contain children");
    }

    private long resourceCount(TaxonomyNode node, String tenantId) {
        if (isSubject(node)) {
            return resourceService.countByTenantIdAndSubjectId(tenantId, node.getId());
        }
        if (isGroup(node)) {
            return resourceService.countByTenantIdAndCategoryId(tenantId, node.getId());
        }
        if (isLesson(node)) {
            return resourceService.countByTenantIdAndSectionId(tenantId, node.getId());
        }
        return 0;
    }

    private void applyNodePlacement(ResourceRecord resource, TaxonomyNode node) {
        if (isSubject(node)) {
            resource.setSubjectId(node.getId());
            return;
        }
        if (isGroup(node)) {
            resource.setSubjectId(node.getSubjectId());
            resource.setCategoryId(node.getId());
            return;
        }
        if (isLesson(node)) {
            resource.setSubjectId(node.getSubjectId());
            resource.setCategoryId(node.getCategoryId());
            resource.setSectionId(node.getId());
            return;
        }
        throw new BadRequestException("unsupported taxonomy node");
    }

    private ResourceRecord newResourceForNode(TaxonomyNode node, AttachResourceRequest request, UserAccessContext access, String tenantId) {
        ResourceRecord resource = new ResourceRecord();
        resource.setId(IdGenerator.newId());
        resource.setTenantId(tenantId);
        resource.setTitle(firstNonBlank(request == null ? null : request.title(), "Untitled resource"));
        resource.setSlug(slug(resource.getTitle()));
        resource.setDocumentTypeId(firstNonBlank(request == null ? null : request.type(), request == null ? null : request.fileType(), "lecture"));
        resource.setThumbnailUrl(request == null ? "" : firstNonBlank(request.thumbnailUrl(), ""));
        resource.setStatus(PUBLISHED);
        resource.setVisibility(OPEN);
        resource.setCreatedBy(access.userId());
        resource.setUpdatedBy(access.userId());
        resource.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        applyNodePlacement(resource, node);
        return resource;
    }

    private void applyResourceRequest(ResourceRecord resource, ResourceRequest body, UserAccessContext access, boolean creating) {
        if (body == null && creating) {
            throw new BadRequestException("resource payload is required");
        }
        if (body == null) {
            return;
        }
        if (creating || body.title() != null) {
            resource.setTitle(required(firstNonBlank(body.title(), resource.getTitle()), "title"));
            resource.setSlug(slug(resource.getTitle()));
        }
        setIfPresent(body.subtitle(), resource::setSubtitle);
        setIfPresent(body.description(), resource::setDescription);
        setIfPresent(body.thumbnailUrl(), resource::setThumbnailUrl);
        if (creating || body.subjectId() != null) {
            resource.setSubjectId(required(firstNonBlank(body.subjectId(), resource.getSubjectId()), "subjectId"));
        }
        setIfPresent(body.categoryId(), resource::setCategoryId);
        setIfPresent(firstNonBlank(body.lessonId(), body.sectionId()), resource::setSectionId);
        setIfPresent(body.programSlug(), resource::setProgramSlug);
        if (creating || body.type() != null || body.documentTypeId() != null) {
            resource.setDocumentTypeId(firstNonBlank(body.type(), body.documentTypeId(), resource.getDocumentTypeId(), "lecture"));
        }
        resource.setStatus(firstNonBlank(body.status(), resource.getStatus(), PUBLISHED));
        resource.setVisibility(normalizeVisibility(firstNonBlank(body.visibility(), resource.getVisibility(), OPEN)));
    }

    private ResourceRequest toResourceRequest(Map<String, Object> body) {
        return new ResourceRequest(
                stringValue(body, "title", null),
                stringValue(body, "subtitle", null),
                stringValue(body, "description", null),
                stringValue(body, "thumbnailUrl", null),
                stringValue(body, "subjectId", null),
                stringValue(body, "categoryId", null),
                stringValue(body, "lessonId", null),
                stringValue(body, "sectionId", null),
                stringValue(body, "type", null),
                stringValue(body, "documentTypeId", null),
                stringValue(body, "status", null),
                stringValue(body, "visibility", null),
                stringValue(body, "programSlug", null)
        );
    }

    private void applyAssetRequest(AssetRecord asset, AssetRequest request, boolean creating) {
        if (request == null && creating) {
            throw new BadRequestException("asset payload is required");
        }
        if (request == null) {
            return;
        }
        if (creating || request.title() != null) {
            asset.setTitle(required(firstNonBlank(request.title(), asset.getTitle()), "title"));
        }
        asset.setSelectedFileType(firstNonBlank(request.selectedFileType(), request.fileType(), asset.getSelectedFileType(), "PPTX"));
        asset.setLaunchMode(firstNonBlank(request.launchMode(), asset.getLaunchMode(), "download_only"));
        setIfPresent(request.originalFileName(), asset::setOriginalFileName);
        setIfPresent(request.detectedMimeType(), asset::setDetectedMimeType);
        setIfPresent(request.fileExtension(), asset::setFileExtension);
        if (request.fileSizeBytes() != null) {
            asset.setFileSizeBytes(request.fileSizeBytes());
        }
        asset.setStorageProvider(firstNonBlank(request.storageProvider(), asset.getStorageProvider(), "local"));
        asset.setStorageUrl(firstNonBlank(request.storageUrl(), asset.getStorageUrl(), ""));
        if (request.metadata() != null) {
            asset.setGoogleSlideManifest(new LinkedHashMap<>(request.metadata()));
        }
        asset.setCanDownload(request.canDownload() == null ? Boolean.TRUE.equals(asset.getCanDownload()) : request.canDownload());
        asset.setStatus(firstNonBlank(request.status(), asset.getStatus(), "ready"));
    }

    private void syncSlideCacheFromMetadata(AssetRecord asset, String tenantId, Map<String, Object> metadata) {
        if (metadata == null || !(metadata.get("slides") instanceof List<?> rawSlides)) {
            return;
        }
        assetService.deleteSlidesByAssetId(asset.getId());
        int fallbackIndex = 1;
        for (Object rawSlide : rawSlides) {
            if (!(rawSlide instanceof Map<?, ?> slideMap)) {
                continue;
            }
            String imageUrl = objectString(slideMap.get("imageUrl"));
            String thumbnailUrl = firstNonBlank(objectString(slideMap.get("thumbnailUrl")), imageUrl);
            if (imageUrl == null) {
                continue;
            }
            AssetSlideCache slide = new AssetSlideCache();
            slide.setId(IdGenerator.newId());
            slide.setTenantId(tenantId);
            slide.setAssetId(asset.getId());
            slide.setSourceVersion(assetSourceVersion(asset));
            slide.setSlideIndex(objectInteger(slideMap.get("index"), fallbackIndex));
            slide.setTitle(firstNonBlank(objectString(slideMap.get("title")), "Slide " + slide.getSlideIndex()));
            slide.setImageUrl(imageUrl);
            slide.setThumbnailUrl(thumbnailUrl);
            slide.setStatus("ready");
            assetService.saveSlide(slide);
            fallbackIndex++;
        }
    }

    private void ensureSchoolAccess(String schoolId, UserAccessContext access) {
        if (!access.canAccessEducationUnit(schoolId)) {
            throw new ForbiddenException("You can only load HocLieu library for managed schools");
        }
    }

    private String requiredSchoolId(String schoolId, UserAccessContext access) {
        String resolved = firstNonBlank(schoolId, access.currentEducationUnitId());
        if (resolved == null) {
            throw new BadRequestException("schoolId is required");
        }
        return resolved;
    }

    private Map<String, Object> scopePayload(UserAccessContext access) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("tenantId", access.tenantId());
        scope.put("currentEducationUnitId", access.currentEducationUnitId());
        scope.put("managedEducationUnitIds", access.managedEducationUnitIds());
        return scope;
    }

    private List<DesignerPresetResponse> designerPresets() {
        return List.of(
                new DesignerPresetResponse("hoclieu-lecture-grid", "Bai giang dien tu", "Hero lon va danh sach bai hoc dang luoi.", "#0891b2", "hero-unit-grid"),
                new DesignerPresetResponse("ebook-clean", "Sach dien tu", "Bia sach gon, uu tien doc tai lieu.", "#1d4ed8", "ebook-card-grid")
        );
    }

    private boolean isSubject(TaxonomyNode node) {
        return SUBJECT_KINDS.contains(normalizeKind(node.getKind()));
    }

    private boolean isGroup(TaxonomyNode node) {
        return GROUP_KINDS.contains(normalizeKind(node.getKind()));
    }

    private boolean isLesson(TaxonomyNode node) {
        return LESSON_KINDS.contains(normalizeKind(node.getKind()));
    }

    private boolean belongsToSubject(TaxonomyNode node, String subjectId) {
        return node.getSubjectId() == null || node.getSubjectId().isBlank() || Objects.equals(node.getSubjectId(), subjectId);
    }

    private boolean belongsToSubject(ResourceRecord resource, String subjectId) {
        return Objects.equals(resource.getSubjectId(), subjectId);
    }

    private boolean isRuntimeVisibleResource(ResourceRecord resource) {
        String status = lowerOrNull(resource.getStatus());
        String visibility = lowerOrNull(resource.getVisibility());
        boolean published = status == null || status.isBlank() || Set.of(PUBLISHED, "active", "ready").contains(status);
        boolean visible = visibility == null || visibility.isBlank() || Set.of(OPEN, "public", "published").contains(visibility);
        return published && visible;
    }

    private Comparator<TaxonomyNode> taxonomyOrder() {
        return Comparator
                .comparing((TaxonomyNode node) -> Optional.ofNullable(node.getSortOrder()).orElse(0))
                .thenComparing(node -> Optional.ofNullable(node.getLabel()).orElse(""));
    }

    private AssetContext requireAssetContext(String assetId, UserAccessContext access) {
        AssetRecord asset = assetService.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu asset not found"));
        String tenantId = normalizeTenant(access.tenantId());
        ResourceRecord resource = resourceService.findById(asset.getResourceId())
                .filter(item -> tenantId.equals(item.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("HocLieu resource not found"));
        return new AssetContext(asset, resource, tenantId);
    }

    private String assetSourceVersion(AssetRecord asset) {
        OffsetDateTime updatedAt = Optional.ofNullable(asset.getUpdatedAt()).orElse(asset.getCreatedAt());
        if (updatedAt != null) {
            return updatedAt.toInstant().toString();
        }
        return Integer.toHexString(Objects.hash(asset.getStorageUrl(), asset.getOriginalFileName(), asset.getFileSizeBytes()));
    }

    private String fallbackLaunchMode(AssetRecord asset) {
        String fileType = firstNonBlank(asset.getSelectedFileType(), asset.getFileExtension(), "");
        if ("google_drive".equalsIgnoreCase(asset.getStorageProvider()) || fileType.toLowerCase(Locale.ROOT).contains("ppt")) {
            return "google_slide_embed";
        }
        return firstNonBlank(asset.getLaunchMode(), "download_only");
    }

    private int requireSlideIndex(int slideIndex) {
        if (slideIndex < 1) {
            throw new BadRequestException("slideIndex must be greater than 0");
        }
        return slideIndex;
    }

    private HocLieuSlideAnnotation newAnnotation(
            String tenantId,
            String userId,
            String schoolId,
            String academicYear,
            String assetId,
            int slideIndex
    ) {
        HocLieuSlideAnnotation annotation = new HocLieuSlideAnnotation();
        annotation.setId(IdGenerator.newId());
        annotation.setTenantId(tenantId);
        annotation.setUserId(userId);
        annotation.setSchoolId(schoolId);
        annotation.setAcademicYear(academicYear);
        annotation.setAssetId(assetId);
        annotation.setSlideIndex(slideIndex);
        return annotation;
    }

    private String currentAcademicYear() {
        int year = OffsetDateTime.now(ZoneOffset.UTC).getYear();
        return year + "-" + (year + 1);
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId.trim();
    }

    private String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }

    private String canonicalKind(String kind) {
        String normalized = blankToNull(kind);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (SUBJECT_KINDS.contains(normalized)) {
            return "subject";
        }
        if (GROUP_KINDS.contains(normalized)) {
            return "category";
        }
        if (LESSON_KINDS.contains(normalized)) {
            return "section";
        }
        return normalized;
    }

    private String stringValue(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null || body.get(key).toString().isBlank()) {
            return fallback;
        }
        return body.get(key).toString().trim();
    }

    private String objectString(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private Integer objectInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String lowerOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeVisibility(String visibility) {
        String normalized = lowerOrNull(visibility);
        if (normalized == null || Set.of("open", "public", "published").contains(normalized)) {
            return OPEN;
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String slug(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private record ResourceLookup(
            Map<String, List<AssetRecord>> assetsByResourceId,
            Map<String, List<ResourceItemRecord>> itemsByResourceId
    ) {
    }

    private record TaxonomyLink(
            String kind,
            String parentId,
            String subjectId,
            String categoryId,
            int depth
    ) {
    }

    private record AssetContext(
            AssetRecord asset,
            ResourceRecord resource,
            String tenantId
    ) {
    }
}
