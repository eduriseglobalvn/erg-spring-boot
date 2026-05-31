package com.erg.ergspringboot.repository.hoclieu;

import com.erg.ergspringboot.model.hoclieu.ResourceRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResourceRecordRepository extends JpaRepository<ResourceRecord, String> {

    @Query("""
            select resource
            from ResourceRecord resource
            where resource.tenantId = :tenantId
              and (:subjectId is null or resource.subjectId = :subjectId)
              and (:categoryId is null or resource.categoryId = :categoryId)
              and (:sectionId is null or resource.sectionId = :sectionId)
              and (:lessonId is null or resource.sectionId = :lessonId or resource.topicId = :lessonId)
              and (:status is null or lower(resource.status) = :status)
              and (
                :visibility is null
                or lower(resource.visibility) = :visibility
                or (
                  :visibility = 'open'
                  and (resource.visibility is null or resource.visibility = '' or lower(resource.visibility) in ('public', 'published'))
                )
              )
            order by resource.updatedAt desc, resource.title asc
            """)
    List<ResourceRecord> findScoped(
            String tenantId,
            String subjectId,
            String categoryId,
            String sectionId,
            String lessonId,
            String status,
            String visibility
    );

    List<ResourceRecord> findByTenantIdAndIdInOrderByUpdatedAtDesc(String tenantId, Collection<String> ids);

    long countByTenantIdAndSubjectId(String tenantId, String subjectId);

    long countByTenantIdAndCategoryId(String tenantId, String categoryId);

    long countByTenantIdAndSectionId(String tenantId, String sectionId);
}
