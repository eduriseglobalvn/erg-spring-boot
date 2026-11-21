package com.erg.ergspringboot.lcms.persistence.quiz.version;

import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface QuizVersionRepository extends JpaRepository<QuizVersion, String> {

    /** The summary columns only: a version row's jsonb snapshot, runtime quiz and answer key can be large. */
    interface Summary {
        String getId();

        String getVersionLabel();

        Integer getVersionNumber();

        String getGradingMode();

        String getContentHash();

        String getPublishedBy();

        java.time.OffsetDateTime getPublishedAt();

        Boolean getIsActive();
    }

    List<Summary> findSummariesByQuizIdAndTenantIdOrderByVersionNumberDesc(
            String quizId, String tenantId, Pageable pageable);

    /** At most one row: (tenant, quiz, idempotency key) is unique (V76). */
    Optional<Summary> findSummaryByQuizIdAndTenantIdAndIdempotencyKey(String quizId, String tenantId, String idempotencyKey);

    /** What the portable bundle reads from a version row: every column except the three jsonb blobs. */
    interface Lineage {
        String getId();

        String getTenantId();

        String getQuizId();

        String getVersionLabel();

        Integer getVersionNumber();

        Integer getSchemaVersion();

        String getGradingMode();

        java.time.OffsetDateTime getPublishedAt();
    }

    Optional<Lineage> findLineageByQuizIdAndTenantIdAndIsActiveTrue(String quizId, String tenantId);

    Optional<Lineage> findLineageByIdAndTenantId(String id, String tenantId);

    List<Lineage> findLineagesByQuizIdAndTenantIdOrderByVersionNumberDesc(String quizId, String tenantId);

    List<Lineage> findLineagesByQuizIdAndTenantIdOrderByVersionNumberDesc(String quizId, String tenantId, Pageable pageable);

    @Query("select v.quizId from QuizVersion v where v.id = :id and v.tenantId = :tenantId")
    Optional<String> findQuizIdByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);

    List<QuizVersion> findByQuizIdAndTenantIdOrderByVersionNumberDesc(String quizId, String tenantId);

    List<QuizVersion> findByQuizIdAndTenantIdOrderByVersionNumberDesc(
            String quizId, String tenantId, Pageable pageable);

    Optional<QuizVersion> findByQuizIdAndTenantIdAndIsActiveTrue(String quizId, String tenantId);

    List<QuizVersion> findByQuizIdInAndTenantIdAndIsActiveTrue(java.util.Collection<String> quizIds, String tenantId);

    /** Quizzes among `quizIds` that a teacher built and that belong to someone other than `userId`. */
    @Query("""
            select count(q) from Quiz q
            where q.tenantId = :tenantId and q.id in :quizIds and q.portal = 'teacher'
            and (q.ownerUserId is null or q.ownerUserId <> :userId)
            """)
    long countTeacherQuizzesNotOwnedBy(
            @Param("tenantId") String tenantId,
            @Param("quizIds") java.util.Collection<String> quizIds,
            @Param("userId") String userId);

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
