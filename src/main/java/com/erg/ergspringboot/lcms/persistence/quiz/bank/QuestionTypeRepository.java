package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTypeRepository extends JpaRepository<QuestionType, String> {
}
