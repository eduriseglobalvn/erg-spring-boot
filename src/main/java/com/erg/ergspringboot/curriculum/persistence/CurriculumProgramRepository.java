package com.erg.ergspringboot.curriculum.persistence;

import com.erg.ergspringboot.curriculum.persistence.CurriculumProgram;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumProgramRepository extends JpaRepository<CurriculumProgram, String> {
    Optional<CurriculumProgram> findByTenantIdAndSubjectIdAndLevelIdAndAcademicYear(
            String tenantId, String subjectId, String levelId, String academicYear);

    /** The published program of a subject in a school year when the level is not known (lowest level id first). */
    Optional<CurriculumProgram> findFirstByTenantIdAndSubjectIdAndAcademicYearAndStatusOrderByLevelIdAsc(
            String tenantId, String subjectId, String academicYear, String status);
}
