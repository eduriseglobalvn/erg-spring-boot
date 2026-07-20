package com.erg.ergspringboot.repository.quiz.bank;

import com.erg.ergspringboot.model.quiz.bank.QuestionBankLevel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankLevelRepository extends JpaRepository<QuestionBankLevel, String> {

    List<QuestionBankLevel> findByTenantIdAndSubjectIdOrderByOrderIndexAsc(String tenantId, String subjectId);
}
