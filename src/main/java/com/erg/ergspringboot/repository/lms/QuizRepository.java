package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Quiz;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, String> {

    List<Quiz> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<Quiz> findByTenantIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(String tenantId, String status);
}
