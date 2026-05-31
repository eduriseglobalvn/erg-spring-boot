package com.erg.ergspringboot.service.hoclieu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.AssetRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.AttachResourceRequest;
import com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.TaxonomyNodeRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.hoclieu.AssetRecord;
import com.erg.ergspringboot.model.hoclieu.HocLieuProgressState;
import com.erg.ergspringboot.model.hoclieu.ResourceItemRecord;
import com.erg.ergspringboot.model.hoclieu.ResourceRecord;
import com.erg.ergspringboot.model.hoclieu.TaxonomyNode;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.hoclieu.AssetRecordRepository;
import com.erg.ergspringboot.repository.hoclieu.AssetSlideCacheRepository;
import com.erg.ergspringboot.repository.hoclieu.HocLieuProgressStateRepository;
import com.erg.ergspringboot.repository.hoclieu.HocLieuSlideAnnotationRepository;
import com.erg.ergspringboot.repository.hoclieu.ResourceItemRecordRepository;
import com.erg.ergspringboot.repository.hoclieu.ResourceRecordRepository;
import com.erg.ergspringboot.repository.hoclieu.TaxonomyNodeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HocLieuServiceTest {

    @Mock
    private TaxonomyNodeRepository taxonomyNodeRepository;
    @Mock
    private ResourceRecordRepository resourceRecordRepository;
    @Mock
    private ResourceItemRecordRepository resourceItemRecordRepository;
    @Mock
    private AssetRecordRepository assetRecordRepository;
    @Mock
    private AssetSlideCacheRepository assetSlideCacheRepository;
    @Mock
    private HocLieuProgressStateRepository progressStateRepository;
    @Mock
    private HocLieuSlideAnnotationRepository slideAnnotationRepository;

    private HocLieuService service;
    private UserAccessContext access;

    @BeforeEach
    void setUp() {
        service = new HocLieuService(
                new HocLieuTaxonomyService(taxonomyNodeRepository),
                new HocLieuResourceService(resourceRecordRepository, resourceItemRecordRepository),
                new HocLieuAssetService(assetRecordRepository, assetSlideCacheRepository),
                new HocLieuProgressService(progressStateRepository, slideAnnotationRepository)
        );
        access = new UserAccessContext("admin-1", "erg", List.of("*"), List.of(), null, null);
    }

    @Test
    void createChildTaxonomyInheritsCorrectSubjectAndCategoryChain() {
        TaxonomyNode subject = node("subject-1", "subject", "IC3 GS6", null, null, null);
        TaxonomyNode group = node("group-1", "category", "Level 1", "subject-1", "subject-1", null);

        when(taxonomyNodeRepository.findById("subject-1")).thenReturn(Optional.of(subject));
        when(taxonomyNodeRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(taxonomyNodeRepository.save(any(TaxonomyNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var createdGroup = service.createChildTaxonomy(
                "subject-1",
                new TaxonomyNodeRequest(null, "Level 1", null, null, null, null, 1, null, "Cap do 1"),
                access
        );
        var createdLesson = service.createChildTaxonomy(
                "group-1",
                new TaxonomyNodeRequest(null, "Bai 01", null, null, null, null, 1, null, null),
                access
        );

        assertThat(createdGroup.kind()).isEqualTo("category");
        assertThat(createdGroup.subjectId()).isEqualTo("subject-1");
        assertThat(createdLesson.kind()).isEqualTo("section");
        assertThat(createdLesson.subjectId()).isEqualTo("subject-1");
        assertThat(createdLesson.categoryId()).isEqualTo("group-1");
    }

    @Test
    void attachResourceCreatesPlacedResourceForSelectedLesson() {
        TaxonomyNode lesson = node("lesson-1", "section", "Bai 01", "group-1", "subject-1", "group-1");

        when(taxonomyNodeRepository.findById("lesson-1")).thenReturn(Optional.of(lesson));
        when(resourceRecordRepository.save(any(ResourceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.attachResource(
                "lesson-1",
                new AttachResourceRequest(null, "Slide bai 01", "lecture", "PPTX", "/thumb.png"),
                access
        );

        assertThat(response.title()).isEqualTo("Slide bai 01");
        assertThat(response.subjectId()).isEqualTo("subject-1");
        assertThat(response.groupId()).isEqualTo("group-1");
        assertThat(response.lessonId()).isEqualTo("lesson-1");
    }

    @Test
    void createAssetUsesSpringStudioPayloadFields() {
        ResourceRecord resource = resource("res-1", "subject-1", "group-1", "lesson-1");

        when(resourceRecordRepository.findById("res-1")).thenReturn(Optional.of(resource));
        when(assetRecordRepository.save(any(AssetRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createAsset(
                "res-1",
                new AssetRequest("Slide bai 01", "PPTX", null, "inline_viewer", "lesson-01.pptx",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx",
                        1024L, "r2", "https://cdn.erg.edu.vn/lesson-01.pptx", true, "ready", null),
                access
        );

        assertThat(response.resourceId()).isEqualTo("res-1");
        assertThat(response.fileType()).isEqualTo("PPTX");
        assertThat(response.launchMode()).isEqualTo("inline_viewer");
        assertThat(response.storageUrl()).contains("lesson-01.pptx");
    }

    @Test
    void createResourceNormalizesLegacyPublicVisibilityToOpen() {
        when(resourceRecordRepository.save(any(ResourceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createResource(
                new com.erg.ergspringboot.common.request.hoclieu.HocLieuStudioRequests.ResourceRequest(
                        "Slide bai 01", null, null, null, "subject-1", "group-1", "lesson-1", null,
                        "lecture", null, "published", "public", "subject-1"
                ),
                access
        );

        assertThat(response.visibility()).isEqualTo("open");
    }


    @Test
    void deleteTaxonomyRejectsNodeThatStillHasChildrenOrResources() {
        TaxonomyNode subject = node("subject-1", "subject", "IC3 GS6", null, null, null);

        when(taxonomyNodeRepository.findById("subject-1")).thenReturn(Optional.of(subject));
        when(taxonomyNodeRepository.countByTenantIdAndParentIdAndStatusIgnoreCase("erg", "subject-1", "active"))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.deleteTaxonomy("subject-1", access))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("children or resources");
    }

    @Test
    void deleteResourceRemovesDependentAssetsAndItemsBeforeResource() {
        ResourceRecord resource = resource("res-1", "subject-1", "group-1", "lesson-1");

        when(resourceRecordRepository.findById("res-1")).thenReturn(Optional.of(resource));

        service.deleteResource("res-1", access);

        verify(assetRecordRepository).deleteByResourceId("res-1");
        verify(resourceItemRecordRepository).deleteByResourceId("res-1");
        verify(resourceRecordRepository).delete(eq(resource));
    }

    @Test
    void libraryBootstrapIncludesResourcesPlacedByResourceItemLessonLink() {
        TaxonomyNode subject = node("subject-1", "subject", "IC3 GS6", null, null, null);
        TaxonomyNode group = node("group-1", "category", "Level 1", "subject-1", "subject-1", null);
        TaxonomyNode lesson = node("lesson-1", "section", "Bai 01", "group-1", "subject-1", "group-1");
        ResourceRecord resource = resource("res-1", "subject-1", "group-1", null);
        AssetRecord asset = googleSlidesAsset("asset-1", resource.getId(), "https://docs.google.com/presentation/d/deck-123/edit");
        ResourceItemRecord item = resourceItem("item-1", resource.getId(), asset.getId(), "group-1", "lesson-1");

        when(taxonomyNodeRepository.findByTenantIdAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc("erg", "active"))
                .thenReturn(List.of(subject, group, lesson));
        when(resourceRecordRepository.findScoped("erg", null, null, null, null, null, null))
                .thenReturn(List.of(resource));
        when(assetRecordRepository.findByResourceIdInOrderByUpdatedAtDesc(List.of("res-1"))).thenReturn(List.of(asset));
        when(resourceItemRecordRepository.findByTenantIdAndResourceIdInOrderBySortOrderAscUnitTitleAscLessonTitleAsc("erg", List.of("res-1")))
                .thenReturn(List.of(item));
        var response = service.libraryBootstrap("school-1", null, access);

        assertThat(response.subjects()).hasSize(1);
        assertThat(response.subjects().getFirst().groups().getFirst().lessons().getFirst().resources())
                .extracting("id")
                .containsExactly("res-1");
        verifyNoInteractions(progressStateRepository);
    }

    @Test
    void libraryBootstrapNormalizesRuntimeVisibilityInsteadOfDroppingStudioPublishedResources() {
        TaxonomyNode subject = node("subject-1", "subject", "IC3 GS6", null, null, null);
        TaxonomyNode group = node("group-1", "category", "Level 1", "subject-1", "subject-1", null);
        TaxonomyNode lesson = node("lesson-1", "section", "Bai 01", "group-1", "subject-1", "group-1");
        ResourceRecord resource = resource("res-1", "subject-1", "group-1", "lesson-1");
        resource.setStatus(" Published ");
        resource.setVisibility(" Public ");
        AssetRecord asset = googleSlidesAsset("asset-1", resource.getId(), "https://docs.google.com/presentation/d/deck-123/edit");

        when(taxonomyNodeRepository.findByTenantIdAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc("erg", "active"))
                .thenReturn(List.of(subject, group, lesson));
        when(resourceRecordRepository.findScoped("erg", null, null, null, null, null, null))
                .thenReturn(List.of(resource));
        when(assetRecordRepository.findByResourceIdInOrderByUpdatedAtDesc(List.of("res-1"))).thenReturn(List.of(asset));
        when(resourceItemRecordRepository.findByTenantIdAndResourceIdInOrderBySortOrderAscUnitTitleAscLessonTitleAsc("erg", List.of("res-1")))
                .thenReturn(List.of());

        var response = service.libraryBootstrap("school-1", null, access);

        assertThat(response.subjects().getFirst().groups().getFirst().lessons().getFirst().resources())
                .extracting("id")
                .containsExactly("res-1");
    }

    @Test
    void libraryProgressReturnsLessonProgressSeparatelyFromBootstrap() {
        TaxonomyNode subject = node("subject-1", "subject", "IC3 GS6", null, null, null);
        TaxonomyNode group = node("group-1", "category", "Level 1", "subject-1", "subject-1", null);
        TaxonomyNode lesson = node("lesson-1", "section", "Bai 01", "group-1", "subject-1", "group-1");
        HocLieuProgressState state = progressState("lesson-1", 60);

        when(taxonomyNodeRepository.findByTenantIdAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc("erg", "active"))
                .thenReturn(List.of(subject, group, lesson));
        when(progressStateRepository.findByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndLessonIdIn(eq("erg"), eq("admin-1"), eq("school-1"), any(), any()))
                .thenReturn(List.of(state));

        var response = service.libraryProgress("school-1", null, access);

        assertThat(response.schoolId()).isEqualTo("school-1");
        assertThat(response.lessons()).hasSize(1);
        assertThat(response.lessons().getFirst().lessonId()).isEqualTo("lesson-1");
        assertThat(response.lessons().getFirst().progressRate()).isEqualTo(60);
    }

    @Test
    void libraryProgressUsesLatestStoredLessonProgressInsteadOfAveragingStaleStates() {
        TaxonomyNode subject = node("subject-1", "subject", "IC3 GS6", null, null, null);
        TaxonomyNode group = node("group-1", "category", "Level 1", "subject-1", "subject-1", null);
        TaxonomyNode lesson = node("lesson-1", "section", "Bai 01", "group-1", "subject-1", "group-1");
        HocLieuProgressState staleComplete = progressState("lesson-1", 100);
        staleComplete.setId("state-stale");
        staleComplete.setUpdatedAt(java.time.OffsetDateTime.parse("2026-05-24T09:00:00Z"));
        HocLieuProgressState latestMarker = progressState("lesson-1", 27);
        latestMarker.setId("state-latest");
        latestMarker.setUpdatedAt(java.time.OffsetDateTime.parse("2026-05-24T10:00:00Z"));

        when(taxonomyNodeRepository.findByTenantIdAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc("erg", "active"))
                .thenReturn(List.of(subject, group, lesson));
        when(progressStateRepository.findByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndLessonIdIn(eq("erg"), eq("admin-1"), eq("school-1"), eq("2025-2026"), any()))
                .thenReturn(List.of(staleComplete, latestMarker));

        var response = service.libraryProgress("school-1", "2025-2026", access);

        assertThat(response.lessons()).hasSize(1);
        assertThat(response.lessons().getFirst().lessonId()).isEqualTo("lesson-1");
        assertThat(response.lessons().getFirst().progressRate()).isEqualTo(27);
    }

    @Test
    void launchGoogleSlidesAssetDoesNotReturnAssistedTrackingMetadata() {
        ResourceRecord resource = resource("res-1", "subject-1", "group-1", "lesson-1");
        AssetRecord asset = googleSlidesAsset("asset-1", resource.getId(), "https://docs.google.com/presentation/d/deck-123/edit");

        when(assetRecordRepository.findById("asset-1")).thenReturn(Optional.of(asset));
        when(resourceRecordRepository.findById("res-1")).thenReturn(Optional.of(resource));
        when(assetSlideCacheRepository.findByTenantIdAndAssetIdAndSourceVersionAndStatusIgnoreCaseOrderBySlideIndexAsc(
                eq("erg"), eq("asset-1"), any(), eq("ready")
        )).thenReturn(List.of());

        var response = service.launch("asset-1", "launch", access);

        assertThat(response).containsEntry("launchMode", "google_slide_embed");
        assertThat(response).containsEntry("slideCount", 0);
        assertThat(response).doesNotContainKey("trackingMode");
        assertThat(response).doesNotContainKey("presentationId");
        assertThat(response).doesNotContainKey("googleSlides");
        assertThat(response).doesNotContainKey("trackingUnavailableReason");
    }

    @Test
    void launchGoogleSlidesAssetUsesManualSlideCountFromLms() {
        ResourceRecord resource = resource("res-1", "subject-1", "group-1", "lesson-1");
        AssetRecord asset = googleSlidesAsset("asset-1", resource.getId(), "https://docs.google.com/presentation/d/deck-123/edit");
        asset.setGoogleSlideManifest(java.util.Map.of("totalSlides", 20));

        when(assetRecordRepository.findById("asset-1")).thenReturn(Optional.of(asset));
        when(resourceRecordRepository.findById("res-1")).thenReturn(Optional.of(resource));
        when(assetSlideCacheRepository.findByTenantIdAndAssetIdAndSourceVersionAndStatusIgnoreCaseOrderBySlideIndexAsc(
                eq("erg"), eq("asset-1"), any(), eq("ready")
        )).thenReturn(List.of());

        var response = service.launch("asset-1", "launch", access);

        assertThat(response).containsEntry("launchMode", "google_slide_embed");
        assertThat(response).containsEntry("slideCount", 20);
        assertThat(response).doesNotContainKey("trackingMode");
        assertThat(response).doesNotContainKey("trackingUnavailableReason");
    }

    private TaxonomyNode node(String id, String kind, String label, String parentId, String subjectId, String categoryId) {
        TaxonomyNode node = new TaxonomyNode();
        node.setId(id);
        node.setTenantId("erg");
        node.setKind(kind);
        node.setLabel(label);
        node.setSlug(label.toLowerCase().replace(" ", "-"));
        node.setParentId(parentId);
        node.setSubjectId(subjectId);
        node.setCategoryId(categoryId);
        node.setStatus("active");
        node.setSortOrder(1);
        return node;
    }

    private ResourceRecord resource(String id, String subjectId, String categoryId, String sectionId) {
        ResourceRecord resource = new ResourceRecord();
        resource.setId(id);
        resource.setTenantId("erg");
        resource.setTitle("Slide bai 01");
        resource.setSubjectId(subjectId);
        resource.setCategoryId(categoryId);
        resource.setSectionId(sectionId);
        resource.setStatus("published");
        resource.setVisibility("open");
        return resource;
    }

    private AssetRecord googleSlidesAsset(String id, String resourceId, String storageUrl) {
        AssetRecord asset = new AssetRecord();
        asset.setId(id);
        asset.setResourceId(resourceId);
        asset.setTitle("Google Slide bai 01");
        asset.setSelectedFileType("PPTX");
        asset.setLaunchMode("google_slide_embed");
        asset.setStorageProvider("google_drive");
        asset.setStorageUrl(storageUrl);
        asset.setCanDownload(false);
        asset.setStatus("ready");
        return asset;
    }

    private ResourceItemRecord resourceItem(String id, String resourceId, String assetId, String unitTitle, String lessonTitle) {
        ResourceItemRecord item = new ResourceItemRecord();
        item.setId(id);
        item.setTenantId("erg");
        item.setResourceId(resourceId);
        item.setAssetId(assetId);
        item.setUnitTitle(unitTitle);
        item.setLessonTitle(lessonTitle);
        item.setSortOrder(1);
        return item;
    }

    private HocLieuProgressState progressState(String lessonId, int progressRate) {
        HocLieuProgressState state = new HocLieuProgressState();
        state.setId("state-" + lessonId);
        state.setTenantId("erg");
        state.setUserId("admin-1");
        state.setSchoolId("school-1");
        state.setAcademicYear("2025-2026");
        state.setLessonId(lessonId);
        state.setProgressRate(progressRate);
        return state;
    }
}
