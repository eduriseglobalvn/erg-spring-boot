package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionBankSubject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankSubjectRepository extends JpaRepository<QuestionBankSubject, String> {

    List<QuestionBankSubject> findByTenantIdOrderByLabelAsc(String tenantId);
}
