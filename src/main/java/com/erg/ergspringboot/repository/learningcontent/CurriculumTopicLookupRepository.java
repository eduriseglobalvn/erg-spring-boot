package com.erg.ergspringboot.repository.learningcontent;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CurriculumTopicLookupRepository extends Repository<com.erg.ergspringboot.model.learningcontent.TopicContentItem, String> {

    @Query(
            value = """
                    select exists (
                        select 1
                        from curriculum_topics topic
                        where topic.id = :topicId
                          and topic.tenant_id = :tenantId
                          and lower(coalesce(topic.status, 'active')) <> 'archived'
                    )
                    """,
            nativeQuery = true
    )
    boolean existsActiveTopicInTenant(String topicId, String tenantId);
}
