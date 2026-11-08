package com.erg.ergspringboot.lcms.persistence.quiz.authoring;

import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.projection.QuizDraftSettingsProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizDraftRepository extends JpaRepository<QuizDraft, String> {

    Optional<QuizDraft> findByIdAndTenantId(String id, String tenantId);

    Optional<QuizDraft> findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc(String quizId, String tenantId);

    @Query("""
            select new com.erg.ergspringboot.lcms.persistence.quiz.authoring.projection.QuizDraftSettingsProjection(
                d.quizId, d.settings, d.updatedAt
            )
            from QuizDraft d
            where d.tenantId = :tenantId and d.quizId in :quizIds
            """)
    List<QuizDraftSettingsProjection> findSettingsByTenantIdAndQuizIds(
            @Param("tenantId") String tenantId,
            @Param("quizIds") Collection<String> quizIds
    );
}
