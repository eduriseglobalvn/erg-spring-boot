package com.erg.ergspringboot.repository.curriculum;

import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import java.util.List;
import java.util.Optional;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface CurriculumSubjectRepository extends JpaRepository<CurriculumSubject, String> {

    Optional<CurriculumSubject> findByTenantIdAndId(String tenantId, String id);

    List<CurriculumSubject> findByTenantIdAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(String tenantId, String status);

    List<CurriculumSubject> findByTenantIdOrderBySortOrderAscNameAsc(String tenantId);

    List<CurriculumSubject> findByTenantIdOrderBySortOrderAscNameAsc(String tenantId, Pageable pageable);

    List<CurriculumSubject> findByTenantIdAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(String tenantId, String status, Pageable pageable);

    boolean existsByTenantIdAndCodeIgnoreCase(String tenantId, String code);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(s.id, s.name, null, null, s.code)
            from CurriculumSubject s
            where s.tenantId = :tenantId and (:status is null or lower(s.status) = lower(:status))
            order by s.sortOrder asc, lower(s.name) asc, s.id asc
            """)
    List<QuestionBankOptionProjection> findAssessmentOptions(@Param("tenantId") String tenantId, @Param("status") String status);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(s.id, s.name, null, null, s.code)
            from CurriculumSubject s
            where s.tenantId = :tenantId and (:status is null or lower(s.status) = lower(:status))
            order by s.sortOrder asc, lower(s.name) asc, s.id asc
            """)
    List<QuestionBankOptionProjection> findAssessmentOptions(
            @Param("tenantId") String tenantId, @Param("status") String status, Pageable pageable);
}
