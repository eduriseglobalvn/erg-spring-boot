package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTypeRepository extends JpaRepository<QuestionType, String> {
}
