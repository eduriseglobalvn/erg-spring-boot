package com.erg.ergspringboot.repository.quiz.bank;

import com.erg.ergspringboot.model.quiz.bank.QuestionBankCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankCategoryRepository extends JpaRepository<QuestionBankCategory, String> {

    List<QuestionBankCategory> findByTenantIdAndSubjectIdAndLevelId(String tenantId, String subjectId, String levelId);
}
