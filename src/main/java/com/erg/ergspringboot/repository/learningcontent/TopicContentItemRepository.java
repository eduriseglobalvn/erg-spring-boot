package com.erg.ergspringboot.repository.learningcontent;

import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface TopicContentItemRepository extends JpaRepository<TopicContentItem, String> {

    Optional<TopicContentItem> findByIdAndTenantIdAndTopicId(String id, String tenantId, String topicId);

    long countByTenantIdAndTopicId(String tenantId, String topicId);

    long countByTenantIdAndTopicIdIn(String tenantId, Collection<String> topicIds);

    @Query("""
            select placement.topicId as topicId, count(placement.id) as contentCount
            from TopicContentItem placement
            where placement.tenantId = :tenantId
              and placement.topicId in :topicIds
            group by placement.topicId
            """)
    List<TopicContentCountProjection> countByTenantIdAndTopicIdInGrouped(String tenantId, Collection<String> topicIds);

    List<TopicContentItem> findAllByTenantIdAndTopicIdAndIdIn(String tenantId, String topicId, Collection<String> ids);

    List<TopicContentItem> findAllByTenantIdAndTopicIdOrderBySortOrderAsc(String tenantId, String topicId);

    List<TopicContentItem> findAllByTenantIdAndTopicIdOrderBySortOrderAsc(
            String tenantId, String topicId, Pageable pageable);

    List<TopicContentItem> findAllByTenantIdAndTopicIdInOrderBySortOrderAsc(String tenantId, Collection<String> topicIds);

    List<TopicContentItem> findAllByTenantIdAndContentItemIdIn(String tenantId, Collection<String> contentItemIds);

    @Query("""
            select coalesce(max(placement.sortOrder), 0) + 1
            from TopicContentItem placement
            where placement.tenantId = :tenantId
              and placement.topicId = :topicId
            """)
    Integer nextSortOrder(String tenantId, String topicId);

    interface TopicContentCountProjection {
        String getTopicId();

        long getContentCount();
    }
}
