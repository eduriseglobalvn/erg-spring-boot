package com.erg.ergspringboot.service.learningcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.repository.learningcontent.ContentAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.CurriculumTopicLookupRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.ContentItemListResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.AttachContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateGoogleSlidesLectureRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.PlaceContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.ReorderTopicContentItemRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class LearningContentServiceTest {

    @Mock
    private ContentItemRepository contentItemRepository;

    @Mock
    private ContentAssetRepository contentAssetRepository;

    @Mock
    private ContentItemAssetRepository contentItemAssetRepository;

    @Mock
    private TopicContentItemRepository topicContentItemRepository;

    @Mock
    private CurriculumTopicLookupRepository curriculumTopicLookupRepository;

    private LearningContentService service;

    @BeforeEach
    void setUp() {
        service = new LearningContentService(
                contentItemRepository,
                contentAssetRepository,
                contentItemAssetRepository,
                topicContentItemRepository,
                curriculumTopicLookupRepository
        );
    }

    @Test
    void createsCanonicalContentItemWithDefaults() {
        when(contentItemRepository.save(any(ContentItem.class))).thenAnswer(invocation -> {
            ContentItem item = invocation.getArgument(0);
            item.setId("content-1");
            return item;
        });

        ContentItem saved = service.createContentItem("tenant-a", "user-1",
                new CreateContentItemRequest("LECTURE", "Internet safety", "Basics", null, null));

        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getContentType()).isEqualTo("LECTURE");
        assertThat(saved.getTitle()).isEqualTo("Internet safety");
        assertThat(saved.getStatus()).isEqualTo("draft");
        assertThat(saved.getVisibility()).isEqualTo("private");
        assertThat(saved.getCreatedBy()).isEqualTo("user-1");
        assertThat(saved.getUpdatedBy()).isEqualTo("user-1");
    }

    @Test
    void createsGoogleSlidesLectureAsOneCanonicalAggregate() {
        when(contentItemRepository.save(any(ContentItem.class))).thenAnswer(invocation -> {
            ContentItem item = invocation.getArgument(0);
            item.setId("content-lecture-1");
            return item;
        });
        when(contentAssetRepository.save(any(ContentAsset.class))).thenAnswer(invocation -> {
            ContentAsset asset = invocation.getArgument(0);
            asset.setId("asset-slide-1");
            return asset;
        });
        ContentItem persistedItem = new ContentItem();
        persistedItem.setId("content-lecture-1");
        persistedItem.setTenantId("tenant-a");
        when(contentItemRepository.findByIdAndTenantId("content-lecture-1", "tenant-a"))
                .thenReturn(Optional.of(persistedItem));
        ContentAsset persistedAsset = new ContentAsset();
        persistedAsset.setId("asset-slide-1");
        persistedAsset.setTenantId("tenant-a");
        when(contentAssetRepository.findByIdAndTenantId("asset-slide-1", "tenant-a"))
                .thenReturn(Optional.of(persistedAsset));
        when(contentItemAssetRepository.save(any(ContentItemAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(curriculumTopicLookupRepository.existsActiveTopicInTenant("topic-1", "tenant-a")).thenReturn(true);
        when(topicContentItemRepository.nextSortOrder("tenant-a", "topic-1")).thenReturn(1);
        when(topicContentItemRepository.save(any(TopicContentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createGoogleSlidesLecture("tenant-a", "user-1", new CreateGoogleSlidesLectureRequest(
                "topic-1",
                "Internet safety",
                "https://docs.google.com/presentation/d/deck-123/embed?start=false",
                "published",
                "public"
        ));

        assertThat(response.contentItem().getContentType()).isEqualTo("LECTURE");
        assertThat(response.contentAsset().getAssetType()).isEqualTo("GOOGLE_SLIDE");
        assertThat(response.contentAsset().getStorageProvider()).isEqualTo("google_slides");
        assertThat(response.placement().getTopicId()).isEqualTo("topic-1");
        assertThat(response.placement().getContentRole()).isEqualTo("LECTURE");
    }

    @Test
    void rejectsNonGoogleSlidesLectureUrlBeforeWriting() {
        assertThatThrownBy(() -> service.createGoogleSlidesLecture("tenant-a", "user-1", new CreateGoogleSlidesLectureRequest(
                "topic-1", "Invalid", "https://example.com/deck", "published", "public"
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Google Slides");
    }

    @Test
    void listsCanonicalContentItemsByTopicWithAssets() {
        TopicContentItem placement = placement("placement-1", 2);
        placement.setContentItemId("content-1");
        when(topicContentItemRepository.findAllByTenantIdAndTopicIdOrderBySortOrderAsc(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("topic-1"),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(placement));
        when(topicContentItemRepository.countByTenantIdAndTopicId("tenant-a", "topic-1"))
                .thenReturn(1L);

        ContentItem item = new ContentItem();
        item.setId("content-1");
        item.setTenantId("tenant-a");
        item.setContentType("LECTURE");
        item.setTitle("Internet safety");
        item.setStatus("published");
        item.setVisibility("public");
        item.setCreatedAt(OffsetDateTime.now().minusDays(1));
        item.setUpdatedAt(OffsetDateTime.now());
        when(contentItemRepository.findAllByTenantIdAndIdIn("tenant-a", List.of("content-1")))
                .thenReturn(List.of(item));
        when(topicContentItemRepository.findAllByTenantIdAndContentItemIdIn("tenant-a", List.of("content-1")))
                .thenReturn(List.of(placement));

        ContentItemAsset link = new ContentItemAsset();
        link.setId("link-1");
        link.setTenantId("tenant-a");
        link.setContentItemId("content-1");
        link.setContentAssetId("asset-1");
        link.setAssetRole("primary");
        link.setSortOrder(1);
        when(contentItemAssetRepository.findAllByTenantIdAndContentItemIdIn("tenant-a", List.of("content-1")))
                .thenReturn(List.of(link));

        ContentAsset asset = new ContentAsset();
        asset.setId("asset-1");
        asset.setTenantId("tenant-a");
        asset.setAssetType("GOOGLE_SLIDE");
        asset.setTitle("Deck");
        asset.setStorageUrl("https://docs.google.com/presentation/d/deck-123/embed");
        asset.setStatus("active");
        asset.setMetadata(Map.of("totalSlides", 12));
        when(contentAssetRepository.findAllByTenantIdAndIdIn("tenant-a", List.of("asset-1")))
                .thenReturn(List.of(asset));

        ContentItemListResponse response = service.listContentItems("tenant-a", "topic-1", 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().title()).isEqualTo("Internet safety");
        assertThat(response.items().getFirst().placements()).extracting("topicId").containsExactly("topic-1");
        assertThat(response.items().getFirst().assets()).extracting("assetType").containsExactly("GOOGLE_SLIDE");
    }

    @Test
    void createsCanonicalContentAssetWithMetadata() {
        when(contentAssetRepository.save(any(ContentAsset.class))).thenAnswer(invocation -> {
            ContentAsset asset = invocation.getArgument(0);
            asset.setId("asset-1");
            return asset;
        });

        ContentAsset saved = service.createContentAsset("tenant-a", "user-1",
                new CreateContentAssetRequest(
                        "google_slide",
                        "Deck",
                        "google_drive",
                        null,
                        "https://docs.google.com/presentation/d/deck-123/embed",
                        "text/html",
                        1024L,
                        null,
                        null,
                        Map.of("totalSlides", 12)
                ));

        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getAssetType()).isEqualTo("GOOGLE_SLIDE");
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getMetadata()).containsEntry("totalSlides", 12);
        assertThat(saved.getCreatedBy()).isEqualTo("user-1");
        assertThat(saved.getUpdatedBy()).isEqualTo("user-1");
    }

    @Test
    void attachesContentAssetOnlyWhenItemAndAssetShareTenant() {
        ContentItem item = new ContentItem();
        item.setId("content-1");
        item.setTenantId("tenant-a");
        ContentAsset asset = new ContentAsset();
        asset.setId("asset-1");
        asset.setTenantId("tenant-a");
        when(contentItemRepository.findByIdAndTenantId("content-1", "tenant-a")).thenReturn(Optional.of(item));
        when(contentAssetRepository.findByIdAndTenantId("asset-1", "tenant-a")).thenReturn(Optional.of(asset));
        when(contentItemAssetRepository.nextSortOrder("tenant-a", "content-1")).thenReturn(3);
        when(contentItemAssetRepository.save(any(ContentItemAsset.class))).thenAnswer(invocation -> {
            ContentItemAsset link = invocation.getArgument(0);
            link.setId("link-1");
            return link;
        });

        ContentItemAsset link = service.attachContentAsset("tenant-a", "content-1", "user-1",
                new AttachContentAssetRequest("asset-1", "primary", null, Map.of("source", "studio")));

        assertThat(link.getTenantId()).isEqualTo("tenant-a");
        assertThat(link.getContentItemId()).isEqualTo("content-1");
        assertThat(link.getContentAssetId()).isEqualTo("asset-1");
        assertThat(link.getAssetRole()).isEqualTo("primary");
        assertThat(link.getSortOrder()).isEqualTo(3);
        assertThat(link.getMetadata()).containsEntry("source", "studio");
    }

    @Test
    void rejectsAttachmentWhenAssetIsMissingForTenant() {
        ContentItem item = new ContentItem();
        item.setId("content-1");
        item.setTenantId("tenant-a");
        when(contentItemRepository.findByIdAndTenantId("content-1", "tenant-a")).thenReturn(Optional.of(item));
        when(contentAssetRepository.findByIdAndTenantId("asset-1", "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachContentAsset("tenant-a", "content-1", "user-1",
                new AttachContentAssetRequest("asset-1", "primary", 1, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("asset");
    }

    @Test
    void placesContentItemOnlyWhenTopicAndContentShareTenant() {
        ContentItem item = new ContentItem();
        item.setId("content-1");
        item.setTenantId("tenant-a");
        item.setStatus("draft");
        when(curriculumTopicLookupRepository.existsActiveTopicInTenant("topic-1", "tenant-a")).thenReturn(true);
        when(contentItemRepository.findByIdAndTenantId("content-1", "tenant-a")).thenReturn(Optional.of(item));
        when(topicContentItemRepository.nextSortOrder("tenant-a", "topic-1")).thenReturn(4);
        when(topicContentItemRepository.save(any(TopicContentItem.class))).thenAnswer(invocation -> {
            TopicContentItem placement = invocation.getArgument(0);
            placement.setId("placement-1");
            return placement;
        });

        TopicContentItem placement = service.placeContentItem("tenant-a", "topic-1", "user-1",
                new PlaceContentItemRequest("content-1", "LECTURE", null, null));

        assertThat(placement.getTenantId()).isEqualTo("tenant-a");
        assertThat(placement.getTopicId()).isEqualTo("topic-1");
        assertThat(placement.getContentItemId()).isEqualTo("content-1");
        assertThat(placement.getContentRole()).isEqualTo("LECTURE");
        assertThat(placement.getSortOrder()).isEqualTo(4);
        assertThat(placement.getStatus()).isEqualTo("active");
    }

    @Test
    void rejectsPlacementWhenTopicIsMissingForTenant() {
        when(curriculumTopicLookupRepository.existsActiveTopicInTenant("topic-1", "tenant-a")).thenReturn(false);

        assertThatThrownBy(() -> service.placeContentItem("tenant-a", "topic-1", "user-1",
                new PlaceContentItemRequest("content-1", "LECTURE", 1, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void removesPlacementWithoutDeletingCanonicalContent() {
        TopicContentItem placement = new TopicContentItem();
        placement.setId("placement-1");
        placement.setTenantId("tenant-a");
        placement.setTopicId("topic-1");
        placement.setContentItemId("content-1");
        when(topicContentItemRepository.findByIdAndTenantIdAndTopicId("placement-1", "tenant-a", "topic-1"))
                .thenReturn(Optional.of(placement));

        service.removePlacement("tenant-a", "topic-1", "placement-1");

        verify(topicContentItemRepository).delete(placement);
    }

    @Test
    void reordersPlacementsWithinTopic() {
        TopicContentItem first = placement("placement-1", 3);
        TopicContentItem second = placement("placement-2", 4);
        when(topicContentItemRepository.findAllByTenantIdAndTopicIdAndIdIn(
                "tenant-a", "topic-1", List.of("placement-2", "placement-1")))
                .thenReturn(List.of(second, first));

        service.reorderPlacements("tenant-a", "topic-1", List.of(
                new ReorderTopicContentItemRequest("placement-2", 1),
                new ReorderTopicContentItemRequest("placement-1", 2)
        ));

        ArgumentCaptor<List<TopicContentItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(topicContentItemRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(TopicContentItem::getId, TopicContentItem::getSortOrder)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("placement-2", 1),
                        org.assertj.core.groups.Tuple.tuple("placement-1", 2)
                );
    }

    @Test
    void rejectsReorderWhenAnyPlacementDoesNotBelongToTopic() {
        when(topicContentItemRepository.findAllByTenantIdAndTopicIdAndIdIn(
                "tenant-a", "topic-1", List.of("placement-1", "placement-missing")))
                .thenReturn(List.of(placement("placement-1", 1)));

        assertThatThrownBy(() -> service.reorderPlacements("tenant-a", "topic-1", List.of(
                new ReorderTopicContentItemRequest("placement-1", 1),
                new ReorderTopicContentItemRequest("placement-missing", 2)
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("same topic");
    }

    private TopicContentItem placement(String id, int sortOrder) {
        TopicContentItem placement = new TopicContentItem();
        placement.setId(id);
        placement.setTenantId("tenant-a");
        placement.setTopicId("topic-1");
        placement.setContentItemId("content-1");
        placement.setSortOrder(sortOrder);
        placement.setStatus("active");
        return placement;
    }
}
