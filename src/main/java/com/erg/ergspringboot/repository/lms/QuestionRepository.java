package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Question;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankQuestionProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, String> {

    Optional<Question> findByIdAndTenantId(String id, String tenantId);

    List<Question> findByTenantIdAndIdIn(String tenantId, List<String> ids);

    List<Question> findByTenantIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(String tenantId, String status);

    @Query("""
            select count(q.id)
            from Question q
            where q.tenantId = :tenantId
              and q.subjectId = :subjectId
              and q.deletedAt is null
            """)
    long countImpactByTenantAndSubject(@Param("tenantId") String tenantId, @Param("subjectId") String subjectId);

    @Query("""
            select count(q.id)
            from Question q
            where q.tenantId = :tenantId
              and q.levelId = :levelId
              and q.deletedAt is null
            """)
    long countImpactByTenantAndLevel(@Param("tenantId") String tenantId, @Param("levelId") String levelId);

    @Query("""
            select count(q.id)
            from Question q
            where q.tenantId = :tenantId
              and q.topicId = :topicId
              and q.deletedAt is null
            """)
    long countImpactByTenantAndTopic(@Param("tenantId") String tenantId, @Param("topicId") String topicId);

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.QuestionBankQuestionProjection(
                        q.id,
                        q.scope,
                        q.subjectId,
                        s.name,
                        q.levelId,
                        l.name,
                        q.topicId,
                        t.name,
                        q.stem,
                        q.objective,
                        q.type,
                        q.difficulty,
                        q.status,
                        q.tags,
                        q.choices,
                        q.answer,
                        q.masteryRate,
                        q.usageCount,
                        q.schoolsUsing,
                        q.updatedAt
                    )
                    from Question q
                    left join Subject s on s.tenantId = q.tenantId and s.id = q.subjectId
                    left join Level l on l.tenantId = q.tenantId and l.id = q.levelId
                    left join Topic t on t.tenantId = q.tenantId and t.id = q.topicId
                    where q.tenantId = :tenantId
                    and lower(coalesce(q.stem, '')) like concat('%', lower(:search), '%')
                    and (:subjectId is null or q.subjectId = :subjectId)
                    and (:levelId is null or q.levelId = :levelId)
                    and (:topicId is null or q.topicId = :topicId)
                    and (:status is null or lower(q.status) = :status)
                    and (:status = 'archived' or q.archivedAt is null)
                    and (:status = 'archived' or :status = 'deleted' or q.deletedAt is null)
                    order by q.updatedAt desc, q.id asc
                    """,
            countQuery = """
                    select count(q.id)
                    from Question q
                    where q.tenantId = :tenantId
                    and lower(coalesce(q.stem, '')) like concat('%', lower(:search), '%')
                    and (:subjectId is null or q.subjectId = :subjectId)
                    and (:levelId is null or q.levelId = :levelId)
                    and (:topicId is null or q.topicId = :topicId)
                    and (:status is null or lower(q.status) = :status)
                    and (:status = 'archived' or q.archivedAt is null)
                    and (:status = 'archived' or :status = 'deleted' or q.deletedAt is null)
                    """
    )
    Page<QuestionBankQuestionProjection> findQuestionBankQuestions(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("topicId") String topicId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.QuestionBankQuestionProjection(
                        q.id,
                        q.scope,
                        q.subjectId,
                        s.name,
                        q.levelId,
                        l.name,
                        q.topicId,
                        t.name,
                        q.stem,
                        q.objective,
                        q.type,
                        q.difficulty,
                        q.status,
                        q.tags,
                        q.choices,
                        q.answer,
                        q.masteryRate,
                        q.usageCount,
                        q.schoolsUsing,
                        q.updatedAt
                    )
                    from Question q
                    left join Subject s on s.tenantId = q.tenantId and s.id = q.subjectId
                    left join Level l on l.tenantId = q.tenantId and l.id = q.levelId
                    left join Topic t on t.tenantId = q.tenantId and t.id = q.topicId
                    where q.tenantId = :tenantId
                    and (:subjectId is null or q.subjectId = :subjectId)
                    and (:levelId is null or q.levelId = :levelId)
                    and (:topicId is null or q.topicId = :topicId)
                    and (:status is null or lower(q.status) = :status)
                    and (:status = 'archived' or q.archivedAt is null)
                    and (:status = 'archived' or :status = 'deleted' or q.deletedAt is null)
                    order by q.updatedAt desc, q.id asc
                    """,
            countQuery = """
                    select count(q.id)
                    from Question q
                    where q.tenantId = :tenantId
                    and (:subjectId is null or q.subjectId = :subjectId)
                    and (:levelId is null or q.levelId = :levelId)
                    and (:topicId is null or q.topicId = :topicId)
                    and (:status is null or lower(q.status) = :status)
                    and (:status = 'archived' or q.archivedAt is null)
                    and (:status = 'archived' or :status = 'deleted' or q.deletedAt is null)
                    """
    )
    Page<QuestionBankQuestionProjection> findQuestionBankQuestionsWithoutSearch(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("topicId") String topicId,
            @Param("status") String status,
            Pageable pageable
    );
}
