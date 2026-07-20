package com.erg.ergspringboot.service.learningcontent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository.LearningResourceCardProjection;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository.TopicContentCountProjection;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningResourceLibraryReadServiceTest {

    @Mock
    private CurriculumSubjectRepository subjectRepository;

    @Mock
    private CurriculumLevelRepository levelRepository;

    @Mock
    private CurriculumTopicRepository topicRepository;

    @Mock
    private TopicContentItemRepository placementRepository;

    @Mock
    private ContentItemRepository contentItemRepository;

    private LearningResourceLibraryReadService service;

    @BeforeEach
    void setUp() {
        service = new LearningResourceLibraryReadService(
                subjectRepository,
                levelRepository,
                topicRepository,
                placementRepository,
                contentItemRepository
        );
    }

    @Test
    void readsPublishedCanonicalResourcesWhenActiveLibraryRequested() {
        CurriculumSubject subject = subject("subject-1", "IC3");
        CurriculumLevel level = level("level-1", "subject-1", "Level 1");
        CurriculumTopic topic = topic("topic-1", "level-1", "Internet safety");
        TopicContentItem placement = placement("placement-1", "topic-1", "content-1", 1);
        ContentItem content = contentItem("content-1", "Internet Safety Practice", "published", "public");

        when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(eq("tenant-a"), eq("active"), any(Pageable.class)))
                .thenReturn(List.of(subject));
        when(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                eq("tenant-a"),
                eq(List.of("subject-1")),
                eq("active"), any(Pageable.class)
        )).thenReturn(List.of(level));
        when(topicRepository.findByTenantIdAndLevelIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                eq("tenant-a"),
                eq(List.of("level-1")),
                eq("active"), any(Pageable.class)
        )).thenReturn(List.of(topic));
        when(placementRepository.countByTenantIdAndTopicIdInGrouped("tenant-a", List.of("topic-1")))
                .thenReturn(List.of(topicCount("topic-1", 1)));
        when(contentItemRepository.findLearningResourceCards(
                org.mockito.ArgumentMatchers.eq("tenant-a"), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("active"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cardProjection("content-1", "subject-1", "level-1", "topic-1",
                        "Internet Safety Practice", "published", "public")), PageRequest.of(0, 20), 1));

        var library = service.learningResourceLibrary(
                "tenant-a",
                null,
                "user-1",
                "2026",
                null,
                null,
                null,
                null,
                null,
                "active",
                null,
                0,
                20
        );

        assertThat(library.resources().totalItems()).isEqualTo(1);
        assertThat(library.resources().items()).hasSize(1);
        var card = library.resources().items().getFirst();
        assertThat(card.id()).isEqualTo("content-1");
        assertThat(card.subjectId()).isEqualTo("subject-1");
        assertThat(card.categoryId()).isEqualTo("level-1");
        assertThat(card.sectionId()).isEqualTo("topic-1");
        assertThat(card.topicId()).isEqualTo("topic-1");
        assertThat(card.status()).isEqualTo("published");
    }

    @Test
    void filtersCanonicalResourcesByLevelBeforePagination() {
        CurriculumSubject subject = subject("subject-1", "IC3");
        CurriculumLevel level = level("level-2", "subject-1", "Level 2");
        CurriculumTopic topic = topic("topic-2", "level-2", "Algorithm basics");
        TopicContentItem placement = placement("placement-2", "topic-2", "content-2", 1);
        ContentItem content = contentItem("content-2", "Algorithm Basics Worksheet", "published", "public");

        when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(eq("tenant-a"), eq("active"), any(Pageable.class)))
                .thenReturn(List.of(subject));
        when(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                eq("tenant-a"),
                eq(List.of("subject-1")),
                eq("active"), any(Pageable.class)
        )).thenReturn(List.of(level));
        when(topicRepository.findByTenantIdAndLevelIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                eq("tenant-a"),
                eq(List.of("level-2")),
                eq("active"), any(Pageable.class)
        )).thenReturn(List.of(topic));
        when(placementRepository.countByTenantIdAndTopicIdInGrouped("tenant-a", List.of("topic-2")))
                .thenReturn(List.of(topicCount("topic-2", 1)));
        when(contentItemRepository.findLearningResourceCards(
                org.mockito.ArgumentMatchers.eq("tenant-a"), any(), org.mockito.ArgumentMatchers.eq("level-2"),
                any(), org.mockito.ArgumentMatchers.eq("active"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cardProjection("content-2", "subject-1", "level-2", "topic-2",
                        "Algorithm Basics Worksheet", "published", "public")), PageRequest.of(0, 1), 1));

        var library = service.learningResourceLibrary(
                "tenant-a",
                null,
                "user-1",
                "2026",
                "subject-1",
                null,
                "level-2",
                null,
                null,
                "active",
                null,
                0,
                1
        );

        assertThat(library.resources().totalItems()).isEqualTo(1);
        assertThat(library.resources().items()).extracting("id").containsExactly("content-2");
        assertThat(library.resources().items().getFirst().categoryId()).isEqualTo("level-2");
    }

    private CurriculumSubject subject(String id, String name) {
        CurriculumSubject subject = new CurriculumSubject();
        subject.setId(id);
        subject.setTenantId("tenant-a");
        subject.setCode(id);
        subject.setName(name);
        subject.setStatus("active");
        subject.setSortOrder(1);
        return subject;
    }

    private CurriculumLevel level(String id, String subjectId, String name) {
        CurriculumLevel level = new CurriculumLevel();
        level.setId(id);
        level.setTenantId("tenant-a");
        level.setSubjectId(subjectId);
        level.setCode(id);
        level.setName(name);
        level.setStatus("active");
        level.setSortOrder(1);
        return level;
    }

    private CurriculumTopic topic(String id, String levelId, String name) {
        CurriculumTopic topic = new CurriculumTopic();
        topic.setId(id);
        topic.setTenantId("tenant-a");
        topic.setLevelId(levelId);
        topic.setCode(id);
        topic.setName(name);
        topic.setStatus("active");
        topic.setSortOrder(1);
        return topic;
    }

    private TopicContentItem placement(String id, String topicId, String contentItemId, int sortOrder) {
        TopicContentItem placement = new TopicContentItem();
        placement.setId(id);
        placement.setTenantId("tenant-a");
        placement.setTopicId(topicId);
        placement.setContentItemId(contentItemId);
        placement.setContentRole("LECTURE");
        placement.setStatus("active");
        placement.setSortOrder(sortOrder);
        return placement;
    }

    private TopicContentCountProjection topicCount(String topicId, long contentCount) {
        return new TopicContentCountProjection() {
            @Override
            public String getTopicId() {
                return topicId;
            }

            @Override
            public long getContentCount() {
                return contentCount;
            }
        };
    }

    private ContentItem contentItem(String id, String title, String status, String visibility) {
        ContentItem item = new ContentItem();
        item.setId(id);
        item.setTenantId("tenant-a");
        item.setContentType("LECTURE");
        item.setTitle(title);
        item.setStatus(status);
        item.setVisibility(visibility);
        item.setUpdatedAt(OffsetDateTime.now());
        return item;
    }

    private LearningResourceCardProjection cardProjection(
            String id, String subjectId, String categoryId, String topicId,
            String title, String status, String visibility
    ) {
        return new LearningResourceCardProjection() {
            public String getId() { return id; }
            public String getSubjectId() { return subjectId; }
            public String getCategoryId() { return categoryId; }
            public String getTopicId() { return topicId; }
            public String getTitle() { return title; }
            public String getSubtitle() { return null; }
            public String getVisibility() { return visibility; }
            public String getStatus() { return status; }
            public java.time.Instant getUpdatedAt() { return java.time.Instant.now(); }
        };
    }
}
