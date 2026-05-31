package com.erg.ergspringboot.repository.hoclieu;

import com.erg.ergspringboot.model.hoclieu.HocLieuProgressState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HocLieuProgressStateRepository extends JpaRepository<HocLieuProgressState, String> {

    List<HocLieuProgressState> findByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndLessonIdIn(
            String tenantId,
            String userId,
            String schoolId,
            String academicYear,
            Collection<String> lessonIds
    );

    Optional<HocLieuProgressState> findFirstByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndSubjectIdAndLessonIdAndResourceIdAndAssetId(
            String tenantId,
            String userId,
            String schoolId,
            String academicYear,
            String subjectId,
            String lessonId,
            String resourceId,
            String assetId
    );
}
