package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionBankCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankCategoryRepository extends JpaRepository<QuestionBankCategory, String> {

    List<QuestionBankCategory> findByTenantIdAndSubjectIdAndLevelId(String tenantId, String subjectId, String levelId);
}
