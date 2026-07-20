package com.erg.ergspringboot.repository.quiz.version;

import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface QuizVersionRepository extends JpaRepository<QuizVersion, String> {

    List<QuizVersion> findByQuizIdAndTenantIdOrderByVersionNumberDesc(String quizId, String tenantId);

    List<QuizVersion> findByQuizIdAndTenantIdOrderByVersionNumberDesc(
            String quizId, String tenantId, Pageable pageable);

    Optional<QuizVersion> findByQuizIdAndTenantIdAndIsActiveTrue(String quizId, String tenantId);

    List<QuizVersion> findByQuizIdInAndTenantIdAndIsActiveTrue(java.util.Collection<String> quizIds, String tenantId);

    Optional<QuizVersion> findByIdAndTenantId(String id, String tenantId);

    Optional<QuizVersion> findTopByQuizIdAndTenantIdOrderByVersionNumberDesc(String quizId, String tenantId);

    @Query(
            value = """
                    select count(v.id)
                    from quiz_versions v
                    join lms_quizzes q
                      on q.tenant_id = v.tenant_id
                     and q.id = v.quiz_id
                    where v.tenant_id = :tenantId
                      and v.published_at is not null
                      and q.deleted_at is null
                      and q.subject_id = :subjectId
                    """,
            nativeQuery = true
    )
    long countPublishedImpactByTenantAndSubject(@Param("tenantId") String tenantId, @Param("subjectId") String subjectId);

    @Query(
            value = """
                    select count(v.id)
                    from quiz_versions v
                    join lms_quizzes q
                      on q.tenant_id = v.tenant_id
                     and q.id = v.quiz_id
                    where v.tenant_id = :tenantId
                      and v.published_at is not null
                      and q.deleted_at is null
                      and q.level_id = :levelId
                    """,
            nativeQuery = true
    )
    long countPublishedImpactByTenantAndLevel(@Param("tenantId") String tenantId, @Param("levelId") String levelId);

    @Query(
            value = """
                    select count(v.id)
                    from quiz_versions v
                    join lms_quizzes q
                      on q.tenant_id = v.tenant_id
                     and q.id = v.quiz_id
                    where v.tenant_id = :tenantId
                      and v.published_at is not null
                      and q.deleted_at is null
                      and (
                          q.category_id = :topicId
                          or jsonb_exists(coalesce(q.topic_ids, '[]'::jsonb), :topicId)
                      )
                    """,
            nativeQuery = true
    )
    long countPublishedImpactByTenantAndTopic(@Param("tenantId") String tenantId, @Param("topicId") String topicId);
}
