package com.erg.ergspringboot.curriculum;

import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumClassSubjectAllocationItem;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumClassSubjectsRequest;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumClassSubjectsResponse;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocation;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocationRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Which subjects each class of a school studies in an academic year; a save replaces the whole set. */
@Service
public class CurriculumClassSubjectService {
    static final int MAX_ALL_YEARS_ROWS = 5_000;

    private final CurriculumClassSubjectAllocationRepository allocations;

    public CurriculumClassSubjectService(CurriculumClassSubjectAllocationRepository allocations) {
        this.allocations = allocations;
    }

    @Transactional(readOnly = true)
    public CurriculumClassSubjectsResponse get(String schoolId, String academicYear, UserAccessContext access,
                                               boolean allSchoolAccess) {
        // No year: every year's rows. The teacher side asks this way, it knows its classes but not the school's year.
        List<CurriculumClassSubjectAllocation> rows = schoolId == null || schoolId.isBlank() ? List.of()
                : academicYear == null || academicYear.isBlank()
                ? allocations.findByTenantIdAndSchoolIdOrderByAcademicClassIdAscSubjectIdAsc(
                        access.tenantId(), schoolId, PageRequest.of(0, MAX_ALL_YEARS_ROWS + 1))
                : allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                        access.tenantId(), schoolId, academicYear);
        if (rows.size() > MAX_ALL_YEARS_ROWS) {
            throw CurriculumException.badRequest("CURRICULUM_ALL_YEARS_TOO_LARGE",
                    "Too many rows without an academicYear; pass academicYear");
        }
        if (!allSchoolAccess && !access.canAccessEducationUnit(schoolId)) {
            // A teacher has no school scope, only classes: they read the subjects of those classes (BUG-A-043).
            rows = rows.stream().filter(row -> access.managedClassIds().contains(row.getAcademicClassId())).toList();
            if (rows.isEmpty()) CurriculumTeachingPlanService.requireScope(schoolId, access, false);
        }
        return toResponse(schoolId, academicYear, rows);
    }

    /**
     * Replaces the whole (school, year) set. {@code expectedVersion} (the version the client last read; 0 for an
     * empty set) turns a concurrent edit into a 409. shortcut: the version is the max row generation, so clearing
     * the set resets it to 0; upgrade to a header row if ABA after a full clear ever matters.
     */
    // The school workspace lists its subjects from these rows.
    @CacheEvict(cacheNames = "lcmsSchoolOperations", allEntries = true)
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
        Map<String, String> levelOf = new LinkedHashMap<>(); // "classId|subjectId" to levelId
        for (CurriculumClassSubjectAllocationItem item : request.allocations()) {
            if (item == null || item.classId() == null || item.classId().isBlank()) {
                throw CurriculumException.badRequest("CURRICULUM_ALLOCATION_CLASS_REQUIRED", "classId is required");
            }
            Set<String> ids = wanted.computeIfAbsent(item.classId().trim(), key -> new LinkedHashSet<>());
            if (item.subjectIds() != null) {
                item.subjectIds().stream().filter(id -> id != null && !id.isBlank()).map(String::trim).forEach(ids::add);
            }
            if (item.levelIds() != null) {
                item.levelIds().forEach((subjectId, levelId) -> {
                    if (levelId != null && !levelId.isBlank()) {
                        levelOf.put(item.classId().trim() + '|' + subjectId.trim(), levelId.trim());
                    }
                });
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
            levelOf.keySet().removeIf(key -> {
                String[] parts = key.split("\\|", 2);
                return !wanted.getOrDefault(parts[0], Set.of()).contains(parts[1]);
            });
            if (!levelOf.isEmpty()) {
                Set<String> knownLevels = Set.copyOf(allocations.existingLevelKeys(tenant, Set.copyOf(levelOf.values())));
                levelOf.forEach((key, levelId) -> {
                    if (!knownLevels.contains(levelId + '|' + key.substring(key.indexOf('|') + 1))) {
                        throw CurriculumException.notFound("level", levelId);
                    }
                });
            }
        }

        assertRemovable(tenant, schoolId, current, wanted);
        allocations.deleteScope(tenant, schoolId, academicYear);
        List<CurriculumClassSubjectAllocation> rows = new ArrayList<>();
        wanted.forEach((classId, subjectIds) -> subjectIds.forEach(subjectId -> {
            CurriculumClassSubjectAllocation row = new CurriculumClassSubjectAllocation();
            row.setTenantId(tenant);
            row.setSchoolId(schoolId);
            row.setAcademicYear(academicYear);
            row.setAcademicClassId(classId);
            row.setSubjectId(subjectId);
            row.setLevelId(levelOf.get(classId + '|' + subjectId));
            row.setVersion(currentVersion + 1);
            row.setCreatedBy(access.userId());
            row.setUpdatedBy(access.userId());
            rows.add(row);
        }));
        return toResponse(schoolId, academicYear, allocations.saveAll(rows));
    }

    /**
     * Owner rule 17/11/2026: a subject with no timetable, class log or teaching hours is removed at once; one that has
     * them can only be removed one month after the latest of those.
     */
    private void assertRemovable(String tenant, String schoolId, List<CurriculumClassSubjectAllocation> current,
                                 Map<String, Set<String>> wanted) {
        Set<String> keptSubjects = new LinkedHashSet<>();
        wanted.values().forEach(keptSubjects::addAll);
        Set<String> removedClasses = new LinkedHashSet<>();
        Set<String> removedPairs = new LinkedHashSet<>();
        Set<String> leavingSubjects = new LinkedHashSet<>(); // leave the school: their teaching hours count too
        for (CurriculumClassSubjectAllocation row : current) {
            if (wanted.getOrDefault(row.getAcademicClassId(), Set.of()).contains(row.getSubjectId())) continue;
            removedClasses.add(row.getAcademicClassId());
            removedPairs.add(row.getAcademicClassId() + '|' + row.getSubjectId());
            if (!keptSubjects.contains(row.getSubjectId())) leavingSubjects.add(row.getSubjectId());
        }
        if (removedPairs.isEmpty()) return;
        LocalDate last = allocations.lastTeachingDate(tenant, removedClasses, removedPairs);
        if (!leavingSubjects.isEmpty()) {
            String month = allocations.lastTeachingHoursMonth(tenant, schoolId, leavingSubjects);
            if (month != null) last = later(last, YearMonth.parse(month).atEndOfMonth());
        }
        if (last == null) return;
        LocalDate allowedFrom = last.plusMonths(1);
        if (LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).isBefore(allowedFrom)) {
            DateTimeFormatter vi = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            throw CurriculumException.badRequest("CURRICULUM_SUBJECT_IN_USE",
                    "Môn này còn lịch dạy, sổ báo giảng hoặc công tiết (gần nhất ngày " + last.format(vi)
                            + "). Chỉ gỡ được từ ngày " + allowedFrom.format(vi) + ".");
        }
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a == null || (b != null && b.isAfter(a)) ? b : a;
    }

    private static long currentVersion(List<CurriculumClassSubjectAllocation> rows) {
        return rows.stream().mapToLong(CurriculumClassSubjectAllocation::getVersion).max().orElse(0L);
    }

    private static CurriculumClassSubjectsResponse toResponse(String schoolId, String academicYear,
                                                              List<CurriculumClassSubjectAllocation> rows) {
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        Map<String, Map<String, String>> levelsByClass = new LinkedHashMap<>();
        rows.forEach(row -> {
            byClass.computeIfAbsent(row.getAcademicClassId(), key -> new ArrayList<>()).add(row.getSubjectId());
            if (row.getLevelId() != null) {
                levelsByClass.computeIfAbsent(row.getAcademicClassId(), key -> new LinkedHashMap<>())
                        .put(row.getSubjectId(), row.getLevelId());
            }
        });
        List<CurriculumClassSubjectAllocationItem> items = new ArrayList<>();
        byClass.forEach((classId, subjectIds) -> items.add(new CurriculumClassSubjectAllocationItem(
                classId, subjectIds, levelsByClass.getOrDefault(classId, Map.of()))));
        return new CurriculumClassSubjectsResponse(schoolId, academicYear, currentVersion(rows), items);
    }
}
