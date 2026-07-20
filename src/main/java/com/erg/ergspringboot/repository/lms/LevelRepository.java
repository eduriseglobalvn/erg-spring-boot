package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface LevelRepository extends JpaRepository<Level, String> {

    List<Level> findByTenantIdAndSubjectIdAndStatusIgnoreCaseOrderByOrderAscNameAsc(String tenantId, String subjectId, String status);

    List<Level> findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(String tenantId, java.util.Set<String> subjectIds, String status);

    List<Level> findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
            String tenantId, java.util.Set<String> subjectIds, String status, Pageable pageable);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(l.id, l.name, l.subjectId, null, l.code)
            from Level l
            where l.tenantId = :tenantId
            and (:subjectId is null or l.subjectId = :subjectId)
            and (:status is null or lower(l.status) = lower(:status))
            order by l.order asc nulls last, lower(l.name) asc, l.id asc
            """)
    List<QuestionBankOptionProjection> findQuestionBankLevelOptions(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("status") String status
    );
}
