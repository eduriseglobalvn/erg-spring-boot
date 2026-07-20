package com.erg.ergspringboot.repository.quiz.version;

import com.erg.ergspringboot.model.quiz.version.QuizVersionQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizVersionQuestionRepository extends JpaRepository<QuizVersionQuestion, String> {

    List<QuizVersionQuestion> findByQuizVersionIdOrderByOrderIndexAsc(String quizVersionId);
}
