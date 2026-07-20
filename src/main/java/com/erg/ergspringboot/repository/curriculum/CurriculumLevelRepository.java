package com.erg.ergspringboot.repository.curriculum;

import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface CurriculumLevelRepository extends JpaRepository<CurriculumLevel, String> {

    Optional<CurriculumLevel> findByTenantIdAndId(String tenantId, String id);

    List<CurriculumLevel> findByTenantIdAndSubjectIdInOrderBySortOrderAscNameAsc(String tenantId, Collection<String> subjectIds);

    List<CurriculumLevel> findByTenantIdAndSubjectIdInOrderBySortOrderAscNameAsc(String tenantId, Collection<String> subjectIds, Pageable pageable);

    List<CurriculumLevel> findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
            String tenantId,
            Collection<String> subjectIds,
            String status
    );

    List<CurriculumLevel> findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
            String tenantId, Collection<String> subjectIds, String status, Pageable pageable
    );

    long countByTenantIdAndSubjectId(String tenantId, String subjectId);

    boolean existsByTenantIdAndSubjectIdAndCodeIgnoreCase(String tenantId, String subjectId, String code);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(l.id, l.name, l.subjectId, null, l.code)
            from CurriculumLevel l
            where l.tenantId = :tenantId and (:subjectId is null or l.subjectId = :subjectId)
              and (:status is null or lower(l.status) = lower(:status))
            order by l.sortOrder asc, lower(l.name) asc, l.id asc
            """)
    List<QuestionBankOptionProjection> findAssessmentOptions(@Param("tenantId") String tenantId, @Param("subjectId") String subjectId, @Param("status") String status);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(l.id, l.name, l.subjectId, null, l.code)
            from CurriculumLevel l
            where l.tenantId = :tenantId and (:subjectId is null or l.subjectId = :subjectId)
              and (:status is null or lower(l.status) = lower(:status))
            order by l.sortOrder asc, lower(l.name) asc, l.id asc
            """)
    List<QuestionBankOptionProjection> findAssessmentOptions(
            @Param("tenantId") String tenantId, @Param("subjectId") String subjectId,
            @Param("status") String status, Pageable pageable);
}
