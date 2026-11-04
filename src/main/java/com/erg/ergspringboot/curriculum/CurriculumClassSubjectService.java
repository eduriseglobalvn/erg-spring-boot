package com.erg.ergspringboot.curriculum;

import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumClassSubjectAllocationItem;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumClassSubjectsRequest;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumClassSubjectsResponse;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocation;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocationRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Which subjects each class of a school studies in an academic year; a save replaces the whole set. */
@Service
public class CurriculumClassSubjectService {
    private final CurriculumClassSubjectAllocationRepository allocations;

    public CurriculumClassSubjectService(CurriculumClassSubjectAllocationRepository allocations) {
        this.allocations = allocations;
    }

    @Transactional(readOnly = true)
    public CurriculumClassSubjectsResponse get(String schoolId, String academicYear, UserAccessContext access,
                                               boolean allSchoolAccess) {
        CurriculumTeachingPlanService.requireScope(schoolId, access, allSchoolAccess);
        return toResponse(schoolId, academicYear, allocations
                .findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                        access.tenantId(), schoolId, academicYear));
    }

    /**
     * Replaces the whole (school, year) set. {@code expectedVersion} (the version the client last read; 0 for an
     * empty set) turns a concurrent edit into a 409. shortcut: the version is the max row generation, so clearing
     * the set resets it to 0; upgrade to a header row if ABA after a full clear ever matters.
     */
    @Transactional
    public CurriculumClassSubjectsResponse replace(String schoolId, String academicYear,
                                                   CurriculumClassSubjectsRequest request, UserAccessContext access,
                                                   boolean allSchoolAccess) {
        CurriculumTeachingPlanService.requireScope(schoolId, access, allSchoolAccess);
        if (academicYear == null || academicYear.isBlank() || academicYear.length() > 16) {
            throw CurriculumException.badRequest("CURRICULUM_ACADEMIC_YEAR_INVALID", "academicYear is required (max 16 chars)");
        }
        if (request == null || request.allocations() == null) {
            throw CurriculumException.badRequest("CURRICULUM_ALLOCATIONS_REQUIRED", "allocations is required");
        }
        String tenant = access.tenantId();
        Map<String, Set<String>> wanted = new LinkedHashMap<>();
        for (CurriculumClassSubjectAllocationItem item : request.allocations()) {
            if (item == null || item.classId() == null || item.classId().isBlank()) {
                throw CurriculumException.badRequest("CURRICULUM_ALLOCATION_CLASS_REQUIRED", "classId is required");
            }
            Set<String> ids = wanted.computeIfAbsent(item.classId().trim(), key -> new LinkedHashSet<>());
            if (item.subjectIds() != null) {
                item.subjectIds().stream().filter(id -> id != null && !id.isBlank()).map(String::trim).forEach(ids::add);
            }
        }
        wanted.values().removeIf(Set::isEmpty);

        List<CurriculumClassSubjectAllocation> current = allocations
                .findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(tenant, schoolId, academicYear);
        long currentVersion = currentVersion(current);
        if (request.expectedVersion() != null && request.expectedVersion() != currentVersion) {
            throw CurriculumException.conflict("CURRICULUM_CLASS_SUBJECTS_STALE",
                    "class subject allocation changed; reload before saving");
        }
        if (!wanted.isEmpty()) {
            Set<String> knownClasses = Set.copyOf(allocations.existingClassIds(tenant, schoolId, wanted.keySet()));
            wanted.keySet().stream().filter(id -> !knownClasses.contains(id)).findFirst().ifPresent(id -> {
                throw CurriculumException.notFound("class", id);
            });
            Set<String> allSubjects = new LinkedHashSet<>();
            wanted.values().forEach(allSubjects::addAll);
            Set<String> knownSubjects = Set.copyOf(allocations.existingSubjectIds(tenant, allSubjects));
            allSubjects.stream().filter(id -> !knownSubjects.contains(id)).findFirst().ifPresent(id -> {
                throw CurriculumException.notFound("subject", id);
            });
        }

        allocations.deleteScope(tenant, schoolId, academicYear);
        List<CurriculumClassSubjectAllocation> rows = new ArrayList<>();
        wanted.forEach((classId, subjectIds) -> subjectIds.forEach(subjectId -> {
            CurriculumClassSubjectAllocation row = new CurriculumClassSubjectAllocation();
            row.setTenantId(tenant);
            row.setSchoolId(schoolId);
            row.setAcademicYear(academicYear);
            row.setAcademicClassId(classId);
            row.setSubjectId(subjectId);
            row.setVersion(currentVersion + 1);
            row.setCreatedBy(access.userId());
            row.setUpdatedBy(access.userId());
            rows.add(row);
        }));
        return toResponse(schoolId, academicYear, allocations.saveAll(rows));
    }

    private static long currentVersion(List<CurriculumClassSubjectAllocation> rows) {
        return rows.stream().mapToLong(CurriculumClassSubjectAllocation::getVersion).max().orElse(0L);
    }

    private static CurriculumClassSubjectsResponse toResponse(String schoolId, String academicYear,
                                                              List<CurriculumClassSubjectAllocation> rows) {
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        rows.forEach(row -> byClass.computeIfAbsent(row.getAcademicClassId(), key -> new ArrayList<>()).add(row.getSubjectId()));
        List<CurriculumClassSubjectAllocationItem> items = new ArrayList<>();
        byClass.forEach((classId, subjectIds) -> items.add(new CurriculumClassSubjectAllocationItem(classId, subjectIds)));
        return new CurriculumClassSubjectsResponse(schoolId, academicYear, currentVersion(rows), items);
    }
}
