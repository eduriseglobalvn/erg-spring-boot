package com.erg.ergspringboot.repository.quiz.bank;

import com.erg.ergspringboot.model.quiz.bank.QuizBankItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizBankItemRepository extends JpaRepository<QuizBankItem, String> {

    List<QuizBankItem> findByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
