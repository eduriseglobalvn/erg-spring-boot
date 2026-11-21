package com.erg.ergspringboot.lcms.persistence.quiz.version;

import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizVersionQuestionRepository extends JpaRepository<QuizVersionQuestion, String> {

    List<QuizVersionQuestion> findByQuizVersionIdOrderByOrderIndexAsc(String quizVersionId);

    @Query("select q.sourceSlideId, q.bankQuestionId from QuizVersionQuestion q where q.quizVersionId = :versionId"
            + " and q.sourceSlideId is not null and q.bankQuestionId is not null and q.bankQuestionId <> '' order by q.orderIndex")
    List<Object[]> findBankLinks(@Param("versionId") String quizVersionId);
}
