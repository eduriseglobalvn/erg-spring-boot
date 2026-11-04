package com.erg.ergspringboot.lcms.persistence.quiz.version;

import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizVersionQuestionRepository extends JpaRepository<QuizVersionQuestion, String> {

    List<QuizVersionQuestion> findByQuizVersionIdOrderByOrderIndexAsc(String quizVersionId);
}
