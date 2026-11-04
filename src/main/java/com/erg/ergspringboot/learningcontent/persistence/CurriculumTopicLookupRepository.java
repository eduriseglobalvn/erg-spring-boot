package com.erg.ergspringboot.learningcontent.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CurriculumTopicLookupRepository extends Repository<com.erg.ergspringboot.learningcontent.persistence.TopicContentItem, String> {

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
