package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizBankItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizBankItemRepository extends JpaRepository<QuizBankItem, String> {

    List<QuizBankItem> findByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
