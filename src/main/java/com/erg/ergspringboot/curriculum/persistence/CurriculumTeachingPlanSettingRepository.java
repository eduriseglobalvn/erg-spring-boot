package com.erg.ergspringboot.curriculum.persistence;

import com.erg.ergspringboot.curriculum.persistence.CurriculumTeachingPlanSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumTeachingPlanSettingRepository extends JpaRepository<CurriculumTeachingPlanSetting, String> {
    Optional<CurriculumTeachingPlanSetting> findByTenantIdAndSchoolIdAndSubjectIdAndAcademicYear(
            String tenantId, String schoolId, String subjectId, String academicYear);
}
