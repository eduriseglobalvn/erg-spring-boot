package com.erg.ergspringboot.repository.curriculum;

import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface CurriculumTopicRepository extends JpaRepository<CurriculumTopic, String> {

    Optional<CurriculumTopic> findByTenantIdAndId(String tenantId, String id);

    List<CurriculumTopic> findByTenantIdAndLevelIdInOrderBySortOrderAscNameAsc(String tenantId, Collection<String> levelIds);

    List<CurriculumTopic> findByTenantIdAndLevelIdInOrderBySortOrderAscNameAsc(String tenantId, Collection<String> levelIds, Pageable pageable);

    List<CurriculumTopic> findByTenantIdAndLevelIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
            String tenantId,
            Collection<String> levelIds,
            String status
    );

    List<CurriculumTopic> findByTenantIdAndLevelIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
            String tenantId, Collection<String> levelIds, String status, Pageable pageable
    );

    long countByTenantIdAndLevelId(String tenantId, String levelId);

    boolean existsByTenantIdAndLevelIdAndCodeIgnoreCase(String tenantId, String levelId, String code);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(t.id, t.name, null, t.levelId, t.code)
            from CurriculumTopic t
            where t.tenantId = :tenantId and (:levelId is null or t.levelId = :levelId)
              and (:status is null or lower(t.status) = lower(:status))
            order by t.sortOrder asc, lower(t.name) asc, t.id asc
            """)
    List<QuestionBankOptionProjection> findAssessmentOptions(@Param("tenantId") String tenantId, @Param("levelId") String levelId, @Param("status") String status);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(t.id, t.name, null, t.levelId, t.code)
            from CurriculumTopic t
            where t.tenantId = :tenantId and (:levelId is null or t.levelId = :levelId)
              and (:status is null or lower(t.status) = lower(:status))
            order by t.sortOrder asc, lower(t.name) asc, t.id asc
            """)
    List<QuestionBankOptionProjection> findAssessmentOptions(
            @Param("tenantId") String tenantId, @Param("levelId") String levelId,
            @Param("status") String status, Pageable pageable);
}
