package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankQuizOptionProjection;
import com.erg.ergspringboot.repository.lms.projection.QuestionUsageCountProjection;
import com.erg.ergspringboot.repository.quiz.authoring.projection.QuizSummaryProjection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, String> {

    List<Quiz> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<Quiz> findByTenantIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(String tenantId, String status);

    java.util.Optional<Quiz> findByIdAndTenantId(String id, String tenantId);

    java.util.Optional<Quiz> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    @Query("""
            select count(q.id)
            from Quiz q
            where q.tenantId = :tenantId
              and q.subjectId = :subjectId
              and q.deletedAt is null
            """)
    long countImpactByTenantAndSubject(@Param("tenantId") String tenantId, @Param("subjectId") String subjectId);

    @Query("""
            select count(q.id)
            from Quiz q
            where q.tenantId = :tenantId
              and q.levelId = :levelId
              and q.deletedAt is null
            """)
    long countImpactByTenantAndLevel(@Param("tenantId") String tenantId, @Param("levelId") String levelId);

    @Query(
            value = """
                    select count(q.id)
                    from lms_quizzes q
                    where q.tenant_id = :tenantId
                      and q.deleted_at is null
                      and (
                          q.category_id = :topicId
                          or jsonb_exists(coalesce(q.topic_ids, '[]'::jsonb), :topicId)
                      )
                    """,
            nativeQuery = true
    )
    long countImpactByTenantAndTopic(@Param("tenantId") String tenantId, @Param("topicId") String topicId);

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.quiz.authoring.projection.QuizSummaryProjection(
                        q.id,
                        q.title,
                        q.subtitle,
                        q.kind,
                        q.status,
                        q.subjectId,
                        q.levelId,
                        q.categoryId,
                        q.questionIds,
                        q.updatedAt
                    )
                    from Quiz q
                    where q.tenantId = :tenantId
                    and q.deletedAt is null
                    and lower(coalesce(q.title, '')) like concat('%', :search, '%')
                    and (:subjectId is null or q.subjectId = :subjectId)
                    and (:levelId is null or q.levelId = :levelId)
                    and (:categoryId is null or q.categoryId = :categoryId)
                    and (:status is null or lower(q.status) = :status)
                    and (:kind is null or lower(q.kind) = :kind)
                    order by q.updatedAt desc, q.id asc
                    """,
            countQuery = """
                    select count(q.id)
                    from Quiz q
                    where q.tenantId = :tenantId
                    and q.deletedAt is null
                    and lower(coalesce(q.title, '')) like concat('%', :search, '%')
                    and (:subjectId is null or q.subjectId = :subjectId)
                    and (:levelId is null or q.levelId = :levelId)
                    and (:categoryId is null or q.categoryId = :categoryId)
                    and (:status is null or lower(q.status) = :status)
                    and (:kind is null or lower(q.kind) = :kind)
                    """
    )
    Page<QuizSummaryProjection> findQuizSummaries(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("categoryId") String categoryId,
            @Param("status") String status,
            @Param("kind") String kind,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.quiz.authoring.projection.QuizSummaryProjection(
                        q.id,
                        q.title,
                        q.subtitle,
                        q.kind,
                        q.status,
                        q.subjectId,
                        q.levelId,
                        q.categoryId,
                        q.questionIds,
                        q.updatedAt
                    )
                    from Quiz q
                    where q.tenantId = :tenantId
                    and q.deletedAt is null
                    and (:subjectId is null or q.subjectId = :subjectId)
                    and (:levelId is null or q.levelId = :levelId)
                    and (:categoryId is null or q.categoryId = :categoryId)
                    and (:status is null or lower(q.status) = :status)
                    and (:kind is null or lower(q.kind) = :kind)
                    order by q.updatedAt desc, q.id asc
                    """,
            countQuery = """
                    select count(q.id)
                    from Quiz q
                    where q.tenantId = :tenantId
                    and q.deletedAt is null
                    and (:subjectId is null or q.subjectId = :subjectId)
                    and (:levelId is null or q.levelId = :levelId)
                    and (:categoryId is null or q.categoryId = :categoryId)
                    and (:status is null or lower(q.status) = :status)
                    and (:kind is null or lower(q.kind) = :kind)
                    """
    )
    Page<QuizSummaryProjection> findQuizSummariesWithoutSearch(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("categoryId") String categoryId,
            @Param("status") String status,
            @Param("kind") String kind,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankQuizOptionProjection(
                q.id,
                q.title,
                q.status,
                q.kind,
                q.subjectId,
                q.levelId,
                q.categoryId,
                q.questionIds,
                q.ownerUserId,
                q.updatedAt
            )
            from Quiz q
            where q.tenantId = :tenantId
            and q.deletedAt is null
            and (:search is null or lower(coalesce(q.title, '')) like lower(concat('%', :search, '%')))
            and (:subjectId is null or q.subjectId = :subjectId)
            and (:levelId is null or q.levelId = :levelId)
            and (:categoryId is null or q.categoryId = :categoryId)
            and lower(q.status) = :status
            and (:kind is null or lower(q.kind) = :kind)
            order by q.updatedAt desc, q.id asc
            """)
    List<QuestionBankQuizOptionProjection> findQuestionBankQuizOptions(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("categoryId") String categoryId,
            @Param("status") String status,
            @Param("kind") String kind
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankQuizOptionProjection(
                q.id,
                q.title,
                q.status,
                q.kind,
                q.subjectId,
                q.levelId,
                q.categoryId,
                q.questionIds,
                q.ownerUserId,
                q.updatedAt
            )
            from Quiz q
            where q.tenantId = :tenantId
            and q.deletedAt is null
            and (:subjectId is null or q.subjectId = :subjectId)
            and (:levelId is null or q.levelId = :levelId)
            and (:categoryId is null or q.categoryId = :categoryId)
            and lower(q.status) = :status
            and (:kind is null or lower(q.kind) = :kind)
            order by q.updatedAt desc, q.id asc
            """)
    List<QuestionBankQuizOptionProjection> findQuestionBankQuizOptionsWithoutSearch(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("categoryId") String categoryId,
            @Param("status") String status,
            @Param("kind") String kind
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankQuizOptionProjection(
                q.id,
                q.title,
                q.status,
                q.kind,
                q.subjectId,
                q.levelId,
                q.categoryId,
                q.questionIds,
                q.ownerUserId,
                q.updatedAt
            )
            from Quiz q
            where q.tenantId = :tenantId
            and q.deletedAt is null
            and (:search is null or lower(coalesce(q.title, '')) like lower(concat('%', :search, '%')))
            and (:subjectId is null or q.subjectId = :subjectId)
            and (:levelId is null or q.levelId = :levelId)
            and (:categoryId is null or q.categoryId = :categoryId)
            and (:kind is null or lower(q.kind) = :kind)
            order by q.updatedAt desc, q.id asc
            """)
    List<QuestionBankQuizOptionProjection> findQuestionBankQuizOptionsWithoutStatus(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("categoryId") String categoryId,
            @Param("kind") String kind
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.QuestionBankQuizOptionProjection(
                q.id,
                q.title,
                q.status,
                q.kind,
                q.subjectId,
                q.levelId,
                q.categoryId,
                q.questionIds,
                q.ownerUserId,
                q.updatedAt
            )
            from Quiz q
            where q.tenantId = :tenantId
            and q.deletedAt is null
            and (:subjectId is null or q.subjectId = :subjectId)
            and (:levelId is null or q.levelId = :levelId)
            and (:categoryId is null or q.categoryId = :categoryId)
            and (:kind is null or lower(q.kind) = :kind)
            order by q.updatedAt desc, q.id asc
            """)
    List<QuestionBankQuizOptionProjection> findQuestionBankQuizOptionsWithoutStatusAndSearch(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("categoryId") String categoryId,
            @Param("kind") String kind
    );

    @Query(
            value = """
                    select question_ref.question_id as questionId, count(distinct quiz.id) as usageCount
                    from lms_quizzes quiz
                    join lateral jsonb_array_elements_text(coalesce(quiz.question_ids, '[]'::jsonb)) as question_ref(question_id)
                        on true
                    where quiz.tenant_id = :tenantId
                    and question_ref.question_id in (:questionIds)
                    group by question_ref.question_id
                    """,
            nativeQuery = true
    )
    List<QuestionUsageCountProjection> countQuestionUsageByQuestionIds(
            @Param("tenantId") String tenantId,
            @Param("questionIds") List<String> questionIds
    );
}
