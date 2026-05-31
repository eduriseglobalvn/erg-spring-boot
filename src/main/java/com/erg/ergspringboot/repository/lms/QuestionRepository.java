package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, String> {

    List<Question> findByTenantIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(String tenantId, String status);
}
