package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, String> {

    List<Subject> findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(String tenantId, String status);

    java.util.Optional<Subject> findByTenantIdAndCodeIgnoreCase(String tenantId, String code);

    List<Subject> findTop500ByTenantIdAndStatusIgnoreCaseOrderByNameAsc(String tenantId, String status);

    List<Subject> findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(
            String tenantId, String status, Pageable pageable);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(s.id, s.name, null, null, s.code)
            from Subject s
            where s.tenantId = :tenantId
            and (:status is null or lower(s.status) = lower(:status))
            order by lower(s.name) asc, s.id asc
            """)
    List<QuestionBankOptionProjection> findQuestionBankSubjectOptions(
            @Param("tenantId") String tenantId,
            @Param("status") String status
    );

    @Query(value = """
            select * from lms_subjects
            where tenant_id = :tenantId
              and scope ->> 'schoolId' = :schoolId
              and lower(coalesce(status, '')) <> 'archived'
            order by name asc
            """, nativeQuery = true)
    List<Subject> findSchoolSubjects(
            @Param("tenantId") String tenantId,
            @Param("schoolId") String schoolId,
            Pageable pageable);
}
