package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.Question;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.projection.QuestionBankQuestionProjection;
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

    // Id-only existence check: the full entity carries the stem, choices and content jsonb.
    @Query("select q.id from Question q where q.tenantId = :tenantId and q.id in :ids")
    List<String> findIdsByTenantIdAndIdIn(@Param("tenantId") String tenantId, @Param("ids") List<String> ids);

    // Only what QuizDraftCompiler reads per bank question (settings); skips the content jsonb.
    @Query("select q.id, q.settings from Question q where q.tenantId = :tenantId and q.id in :ids")
    List<Object[]> findSettingsByTenantIdAndIdIn(@Param("tenantId") String tenantId, @Param("ids") List<String> ids);

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

    @Query("""
            select q.topicId, count(q.id)
            from Question q
            where q.tenantId = :tenantId
              and q.topicId in :topicIds
              and q.archivedAt is null
              and q.deletedAt is null
            group by q.topicId
            """)
    List<Object[]> countActiveByTopic(@Param("tenantId") String tenantId, @Param("topicIds") List<String> topicIds);

    // The list rows are built from one select list; the "Slim" queries leave out the content jsonb (runtimeQuestion) that
    // only a caller selecting runtimeQuestion needs.
    String ROW_SELECT = """
            select new com.erg.ergspringboot.lcms.persistence.quiz.bank.projection.QuestionBankQuestionProjection(
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
                q.updatedAt""";
    String ROW_FROM = """

            )
            from Question q
            left join CurriculumSubject s on s.tenantId = q.tenantId and s.id = q.subjectId
            left join CurriculumLevel l on l.tenantId = q.tenantId and l.id = q.levelId
            left join CurriculumTopic t on t.tenantId = q.tenantId and t.id = q.topicId
            where q.tenantId = :tenantId
            and\s""";
    String FILTERS = """

            and (:subjectId is null or q.subjectId = :subjectId)
            and (:levelId is null or q.levelId = :levelId)
            and (:topicId is null or q.topicId = :topicId)
            and (:status is null or lower(q.status) = :status)
            and (:status = 'archived' or q.archivedAt is null)
            and (:status = 'archived' or :status = 'deleted' or q.deletedAt is null)
            """;
    String ORDER = "order by q.updatedAt desc, q.id asc";
    String COUNT_SEARCH = """
            select count(q.id)
            from Question q
            where q.tenantId = :tenantId
            and\s""" + VietnameseFold.STEM_CONTAINS_SEARCH + FILTERS;
    String COUNT_NO_SEARCH = """
            select count(q.id)
            from Question q
            where q.tenantId = :tenantId
            and (:subjectId is null or q.subjectId = :subjectId)
            and (:levelId is null or q.levelId = :levelId)
            and (:topicId is null or q.topicId = :topicId)
            and (:status is null or lower(q.status) = :status)
            and (:status = 'archived' or q.archivedAt is null)
            and (:status = 'archived' or :status = 'deleted' or q.deletedAt is null)
            """;
    String NO_SEARCH_FROM = """

            )
            from Question q
            left join CurriculumSubject s on s.tenantId = q.tenantId and s.id = q.subjectId
            left join CurriculumLevel l on l.tenantId = q.tenantId and l.id = q.levelId
            left join CurriculumTopic t on t.tenantId = q.tenantId and t.id = q.topicId
            where q.tenantId = :tenantId
            """;
    String NO_SEARCH_FILTERS = """
            and (:subjectId is null or q.subjectId = :subjectId)
            and (:levelId is null or q.levelId = :levelId)
            and (:topicId is null or q.topicId = :topicId)
            and (:status is null or lower(q.status) = :status)
            and (:status = 'archived' or q.archivedAt is null)
            and (:status = 'archived' or :status = 'deleted' or q.deletedAt is null)
            """;

    @Query(value = ROW_SELECT + ", q.content" + ROW_FROM + VietnameseFold.STEM_CONTAINS_SEARCH + FILTERS + ORDER,
            countQuery = COUNT_SEARCH)
    Page<QuestionBankQuestionProjection> findQuestionBankQuestions(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("topicId") String topicId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = ROW_SELECT + ROW_FROM + VietnameseFold.STEM_CONTAINS_SEARCH + FILTERS + ORDER, countQuery = COUNT_SEARCH)
    Page<QuestionBankQuestionProjection> findQuestionBankQuestionsSlim(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("topicId") String topicId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = ROW_SELECT + ", q.content" + NO_SEARCH_FROM + NO_SEARCH_FILTERS + ORDER, countQuery = COUNT_NO_SEARCH)
    Page<QuestionBankQuestionProjection> findQuestionBankQuestionsWithoutSearch(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("topicId") String topicId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = ROW_SELECT + NO_SEARCH_FROM + NO_SEARCH_FILTERS + ORDER, countQuery = COUNT_NO_SEARCH)
    Page<QuestionBankQuestionProjection> findQuestionBankQuestionsWithoutSearchSlim(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("levelId") String levelId,
            @Param("topicId") String topicId,
            @Param("status") String status,
            Pageable pageable
    );
}
