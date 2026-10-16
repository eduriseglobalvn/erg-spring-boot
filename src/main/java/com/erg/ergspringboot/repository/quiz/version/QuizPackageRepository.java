package com.erg.ergspringboot.repository.quiz.version;

import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizPackageRepository extends JpaRepository<QuizPackage, String> {

    Optional<QuizPackage> findByIdAndTenantId(String id, String tenantId);

    Optional<QuizPackage> findByQuizVersionId(String quizVersionId);

    Optional<QuizPackage> findByQuizVersionIdAndTenantId(String quizVersionId, String tenantId);
}
