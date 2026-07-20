package com.erg.ergspringboot.repository.learningcontent;

import com.erg.ergspringboot.model.learningcontent.ContentItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentItemRepository extends JpaRepository<ContentItem, String> {

    Optional<ContentItem> findByIdAndTenantId(String id, String tenantId);

    List<ContentItem> findAllByTenantIdOrderByUpdatedAtDesc(String tenantId, Pageable pageable);

    List<ContentItem> findAllByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    long countByTenantId(String tenantId);

    @Query(value = """
            select * from (
                select distinct on (item.id)
                    item.id as "id",
                    level.subject_id as "subjectId",
                    level.id as "categoryId",
                    topic.id as "topicId",
                    item.title as "title",
                    item.description as "subtitle",
                    item.visibility as "visibility",
                    item.status as "status",
                    item.updated_at as "updatedAt"
                from content_items item
                join topic_content_items placement
                  on placement.tenant_id = item.tenant_id
                 and placement.content_item_id = item.id
                join curriculum_topics topic
                  on topic.tenant_id = placement.tenant_id
                 and topic.id = placement.topic_id
                join curriculum_levels level
                  on level.tenant_id = topic.tenant_id
                 and level.id = topic.level_id
                where item.tenant_id = :tenantId
                  and (:subjectId is null or level.subject_id = :subjectId)
                  and (:categoryId is null or level.id = :categoryId)
                  and (:topicId is null or topic.id = :topicId)
                  and (:visibility is null or lower(item.visibility) = lower(:visibility))
                  and (
                    :status is null
                    or (:status = 'active' and lower(item.status) in ('active', 'published'))
                    or lower(item.status) = lower(:status)
                  )
                order by item.id, placement.sort_order asc, placement.id asc
            ) cards
            order by cards."updatedAt" desc nulls last, cards.id asc
            """, countQuery = """
            select count(distinct item.id)
            from content_items item
            join topic_content_items placement
              on placement.tenant_id = item.tenant_id
             and placement.content_item_id = item.id
            join curriculum_topics topic
              on topic.tenant_id = placement.tenant_id
             and topic.id = placement.topic_id
            join curriculum_levels level
              on level.tenant_id = topic.tenant_id
             and level.id = topic.level_id
            where item.tenant_id = :tenantId
              and (:subjectId is null or level.subject_id = :subjectId)
              and (:categoryId is null or level.id = :categoryId)
              and (:topicId is null or topic.id = :topicId)
              and (:visibility is null or lower(item.visibility) = lower(:visibility))
              and (
                :status is null
                or (:status = 'active' and lower(item.status) in ('active', 'published'))
                or lower(item.status) = lower(:status)
              )
            """, nativeQuery = true)
    Page<LearningResourceCardProjection> findLearningResourceCards(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("categoryId") String categoryId,
            @Param("topicId") String topicId,
            @Param("status") String status,
            @Param("visibility") String visibility,
            Pageable pageable
    );

    interface LearningResourceCardProjection {
        String getId();
        String getSubjectId();
        String getCategoryId();
        String getTopicId();
        String getTitle();
        String getSubtitle();
        String getVisibility();
        String getStatus();
        java.time.Instant getUpdatedAt();
    }
}
