package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Level;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<Level, String> {

    List<Level> findByTenantIdAndSubjectIdAndStatusIgnoreCaseOrderByOrderAscNameAsc(String tenantId, String subjectId, String status);
}
