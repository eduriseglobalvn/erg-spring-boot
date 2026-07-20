package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.QuizQuestion;
import com.erg.ergspringboot.repository.lms.projection.QuestionUsageCountProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, String> {

    List<QuizQuestion> findByTenantIdAndQuizIdOrderByOrderIndexAsc(String tenantId, String quizId);

    @Modifying
    void deleteByTenantIdAndQuizId(String tenantId, String quizId);

    @Query(
            value = """
                    select link.question_id as questionId, count(distinct link.quiz_id) as usageCount
                    from lms_quiz_questions link
                    join lms_quizzes quiz
                        on quiz.id = link.quiz_id
                        and quiz.tenant_id = link.tenant_id
                    where link.tenant_id = :tenantId
                    and quiz.deleted_at is null
                    and link.question_id in (:questionIds)
                    group by link.question_id
                    """,
            nativeQuery = true
    )
    List<QuestionUsageCountProjection> countUsageByQuestionIds(
            @Param("tenantId") String tenantId,
            @Param("questionIds") List<String> questionIds
    );
}
