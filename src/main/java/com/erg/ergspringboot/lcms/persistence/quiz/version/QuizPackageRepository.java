package com.erg.ergspringboot.lcms.persistence.quiz.version;

import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizPackageRepository extends JpaRepository<QuizPackage, String> {

    Optional<QuizPackage> findByIdAndTenantId(String id, String tenantId);

    Optional<QuizPackage> findByQuizVersionId(String quizVersionId);

    Optional<QuizPackage> findByQuizVersionIdAndTenantId(String quizVersionId, String tenantId);

    List<QuizPackage> findByQuizVersionIdInAndTenantId(java.util.Collection<String> quizVersionIds, String tenantId);

    @Query("select p.quizId from QuizPackage p where p.id = :id and p.tenantId = :tenantId")
    Optional<String> findQuizIdByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);

    /** One round trip for an attempt start: the quiz and (when it belongs to the quiz) the pinned version's package; rows are {quiz, package-or-null}. */
    @Query("select q, p from Quiz q left join QuizPackage p on p.quizId = q.id and p.tenantId = q.tenantId "
            + "and p.quizVersionId = :versionId where q.id = :quizId and q.tenantId = :tenantId")
    List<Object[]> findQuizWithVersionPackage(@Param("tenantId") String tenantId, @Param("quizId") String quizId,
            @Param("versionId") String versionId);
}
