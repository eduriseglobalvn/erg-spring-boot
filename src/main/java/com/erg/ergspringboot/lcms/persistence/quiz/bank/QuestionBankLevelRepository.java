package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionBankLevel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankLevelRepository extends JpaRepository<QuestionBankLevel, String> {

    List<QuestionBankLevel> findByTenantIdAndSubjectIdOrderByOrderIndexAsc(String tenantId, String subjectId);
}
