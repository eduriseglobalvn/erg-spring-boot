package com.erg.ergspringboot.repository.hoclieu;

import com.erg.ergspringboot.model.hoclieu.HocLieuSlideAnnotation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HocLieuSlideAnnotationRepository extends JpaRepository<HocLieuSlideAnnotation, String> {

    Optional<HocLieuSlideAnnotation> findFirstByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndAssetIdAndSlideIndex(
            String tenantId,
            String userId,
            String schoolId,
            String academicYear,
            String assetId,
            Integer slideIndex
    );
}
