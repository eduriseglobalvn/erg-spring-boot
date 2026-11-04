package com.erg.ergspringboot.lcms.persistence.quiz.authoring;

import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizDraftRepository extends JpaRepository<QuizDraft, String> {

    Optional<QuizDraft> findByIdAndTenantId(String id, String tenantId);

    Optional<QuizDraft> findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc(String quizId, String tenantId);
}
