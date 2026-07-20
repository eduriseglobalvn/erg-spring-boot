package com.erg.ergspringboot.repository.quiz.version;

import com.erg.ergspringboot.model.quiz.version.QuizVersionSection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizVersionSectionRepository extends JpaRepository<QuizVersionSection, String> {

    List<QuizVersionSection> findByQuizVersionIdOrderByOrderIndexAsc(String quizVersionId);
}
