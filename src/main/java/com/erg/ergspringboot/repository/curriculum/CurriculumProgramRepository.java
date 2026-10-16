package com.erg.ergspringboot.repository.curriculum;

import com.erg.ergspringboot.model.curriculum.CurriculumProgram;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumProgramRepository extends JpaRepository<CurriculumProgram, String> {
    Optional<CurriculumProgram> findByTenantIdAndSubjectIdAndLevelIdAndAcademicYear(
            String tenantId, String subjectId, String levelId, String academicYear);
}
