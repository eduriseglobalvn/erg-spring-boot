package com.erg.ergspringboot.service.hoclieu;

import com.erg.ergspringboot.model.hoclieu.HocLieuProgressState;
import com.erg.ergspringboot.model.hoclieu.HocLieuSlideAnnotation;
import com.erg.ergspringboot.repository.hoclieu.HocLieuProgressStateRepository;
import com.erg.ergspringboot.repository.hoclieu.HocLieuSlideAnnotationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class HocLieuProgressService {

    private final HocLieuProgressStateRepository progressRepository;
    private final HocLieuSlideAnnotationRepository annotationRepository;

    public HocLieuProgressService(HocLieuProgressStateRepository progressRepository, HocLieuSlideAnnotationRepository annotationRepository) {
        this.progressRepository = progressRepository;
        this.annotationRepository = annotationRepository;
    }

    public List<HocLieuProgressState> findByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndLessonIdIn(
            String tenantId,
            String userId,
            String schoolId,
            String academicYear,
            Collection<String> lessonIds
    ) {
        return progressRepository.findByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndLessonIdIn(tenantId, userId, schoolId, academicYear, lessonIds);
    }

    public Optional<HocLieuProgressState> findFirstProgress(
            String tenantId,
            String userId,
            String schoolId,
            String academicYear,
            String subjectId,
            String lessonId,
            String resourceId,
            String assetId
    ) {
        return progressRepository.findFirstByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndSubjectIdAndLessonIdAndResourceIdAndAssetId(
                tenantId, userId, schoolId, academicYear, subjectId, lessonId, resourceId, assetId);
    }

    public Optional<HocLieuSlideAnnotation> findFirstAnnotation(String tenantId, String userId, String schoolId, String academicYear, String assetId, Integer slideIndex) {
        return annotationRepository.findFirstByTenantIdAndUserIdAndSchoolIdAndAcademicYearAndAssetIdAndSlideIndex(
                tenantId, userId, schoolId, academicYear, assetId, slideIndex);
    }

    @Transactional("postgresTransactionManager")
    public HocLieuProgressState save(HocLieuProgressState progress) {
        return progressRepository.save(progress);
    }

    @Transactional("postgresTransactionManager")
    public HocLieuSlideAnnotation saveAnnotation(HocLieuSlideAnnotation annotation) {
        return annotationRepository.save(annotation);
    }
}
