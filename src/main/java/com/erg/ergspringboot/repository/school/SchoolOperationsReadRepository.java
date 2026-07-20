package com.erg.ergspringboot.repository.school;

import com.erg.ergspringboot.model.school.SchoolOperationalProfile;
import com.erg.ergspringboot.repository.school.projection.SchoolDirectoryRowProjection;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SchoolOperationsReadRepository extends JpaRepository<SchoolOperationalProfile, String> {

    @Query(value = """
            SELECT eu.id AS id, eu.name AS name, eu.slug AS slug,
                   profile.school_type AS "schoolType", profile.onboarding_status AS status,
                   profile.district AS district, year.start_year AS "academicYearStart",
                   year.end_year AS "academicYearEnd",
                   (SELECT count(*) FROM academic_classes class
                     WHERE class.tenant_id = :tenantId AND class.school_id = eu.id
                       AND lower(coalesce(class.status, '')) <> 'archived') AS "classCount",
                   (SELECT count(*) FROM lms_students student
                     WHERE student.tenant_id = :tenantId AND student.school_id = eu.id
                       AND lower(coalesce(student.status, '')) <> 'archived') AS "studentCount",
                   profile.version AS version
            FROM education_units eu
            JOIN school_operational_profiles profile
              ON profile.school_id = eu.id AND profile.tenant_id = :tenantId
            LEFT JOIN school_academic_years year ON year.id = profile.current_academic_year_id
            WHERE eu.tenant_id = :tenantId
              AND (:allScope = true OR eu.id IN (:schoolIds))
              AND (:search IS NULL OR lower(eu.name) LIKE concat('%', :search, '%')
                   OR lower(coalesce(eu.slug, '')) LIKE concat('%', :search, '%'))
              AND (:status IS NULL OR lower(profile.onboarding_status) = :status)
              AND (:schoolType IS NULL OR profile.school_type = :schoolType)
            ORDER BY eu.name ASC
            """, countQuery = """
            SELECT count(eu.id)
            FROM education_units eu
            JOIN school_operational_profiles profile
              ON profile.school_id = eu.id AND profile.tenant_id = :tenantId
            WHERE eu.tenant_id = :tenantId
              AND (:allScope = true OR eu.id IN (:schoolIds))
              AND (:search IS NULL OR lower(eu.name) LIKE concat('%', :search, '%')
                   OR lower(coalesce(eu.slug, '')) LIKE concat('%', :search, '%'))
              AND (:status IS NULL OR lower(profile.onboarding_status) = :status)
              AND (:schoolType IS NULL OR profile.school_type = :schoolType)
            """, nativeQuery = true)
    Page<SchoolDirectoryRowProjection> findDirectory(
            String tenantId,
            Collection<String> schoolIds,
            boolean allScope,
            String search,
            String status,
            String schoolType,
            Pageable pageable
    );
}
