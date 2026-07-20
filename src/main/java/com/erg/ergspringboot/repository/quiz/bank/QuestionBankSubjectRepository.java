package com.erg.ergspringboot.repository.quiz.bank;

import com.erg.ergspringboot.model.quiz.bank.QuestionBankSubject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankSubjectRepository extends JpaRepository<QuestionBankSubject, String> {

    List<QuestionBankSubject> findByTenantIdOrderByLabelAsc(String tenantId);
}
