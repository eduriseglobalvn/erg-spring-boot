package com.erg.ergspringboot.curriculum.persistence;

import com.erg.ergspringboot.curriculum.persistence.CurriculumProgramRow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurriculumProgramRowRepository extends JpaRepository<CurriculumProgramRow, String> {
    List<CurriculumProgramRow> findByTenantIdAndProgramIdOrderBySortOrderAscPeriodAsc(String tenantId, String programId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CurriculumProgramRow r WHERE r.tenantId = :tenantId AND r.programId = :programId")
    void deleteByTenantIdAndProgramId(@Param("tenantId") String tenantId, @Param("programId") String programId);
}
