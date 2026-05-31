package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Subject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, String> {

    List<Subject> findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(String tenantId, String status);
}
