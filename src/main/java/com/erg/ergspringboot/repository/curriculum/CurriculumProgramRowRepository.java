package com.erg.ergspringboot.repository.curriculum;

import com.erg.ergspringboot.model.curriculum.CurriculumProgramRow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumProgramRowRepository extends JpaRepository<CurriculumProgramRow, String> {
    List<CurriculumProgramRow> findByTenantIdAndProgramIdOrderBySortOrderAscPeriodAsc(String tenantId, String programId);
    void deleteByTenantIdAndProgramId(String tenantId, String programId);
}
