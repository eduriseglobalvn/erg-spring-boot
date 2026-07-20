package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Topic;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, String> {

    List<Topic> findByTenantIdAndLevelIdAndStatusIgnoreCaseOrderByOrderAscNameAsc(String tenantId, String levelId, String status);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection(t.id, t.name, null, t.levelId, t.code)
            from Topic t
            where t.tenantId = :tenantId
            and (:levelId is null or t.levelId = :levelId)
            and (:status is null or lower(t.status) = lower(:status))
            order by t.order asc nulls last, lower(t.name) asc, t.id asc
            """)
    List<QuestionBankOptionProjection> findQuestionBankTopicOptions(
            @Param("tenantId") String tenantId,
            @Param("levelId") String levelId,
            @Param("status") String status
    );
}
