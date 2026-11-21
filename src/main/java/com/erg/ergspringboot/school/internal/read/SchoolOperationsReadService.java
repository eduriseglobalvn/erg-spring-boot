package com.erg.ergspringboot.school.internal.read;

import static com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.*;
import static com.erg.ergspringboot.common.response.school.SchoolResponses.*;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.school.persistence.SchoolAcademicYear;
import com.erg.ergspringboot.school.persistence.SchoolGrade;
import com.erg.ergspringboot.school.persistence.SchoolOperationalProfile;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocation;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocationRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubject;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubjectRepository;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.projection.SchoolStudentStatsProjection;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import com.erg.ergspringboot.school.persistence.SchoolAcademicYearRepository;
import com.erg.ergspringboot.school.persistence.SchoolGradeRepository;
import com.erg.ergspringboot.school.persistence.SchoolOperationalProfileRepository;
import com.erg.ergspringboot.school.persistence.SchoolOperationsReadRepository;
import com.erg.ergspringboot.school.persistence.projection.SchoolDirectoryRowProjection;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolOperationsReadService {
    private static final int MAX_STUDENT_PAGE_SIZE = 200;
    private static final int MAX_WORKSPACE_CLASSES = 500;
    private static final int MAX_WORKSPACE_SUBJECTS = 500;
    private final SchoolOperationsReadRepository readRepository;
    private final EducationUnitRepository unitRepository;
    private final SchoolOperationalProfileRepository profileRepository;
    private final SchoolAcademicYearRepository yearRepository;
    private final SchoolGradeRepository gradeRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final CurriculumSubjectRepository subjectRepository;
    private final CurriculumClassSubjectAllocationRepository allocationRepository;

    public SchoolOperationsReadService(SchoolOperationsReadRepository readRepository, EducationUnitRepository unitRepository,
            SchoolOperationalProfileRepository profileRepository, SchoolAcademicYearRepository yearRepository,
            SchoolGradeRepository gradeRepository, ClassroomRepository classroomRepository, StudentRepository studentRepository,
            CurriculumSubjectRepository subjectRepository,
            CurriculumClassSubjectAllocationRepository allocationRepository) {
        this.readRepository = readRepository; this.unitRepository = unitRepository; this.profileRepository = profileRepository;
        this.yearRepository = yearRepository; this.gradeRepository = gradeRepository;
        this.classroomRepository = classroomRepository; this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "lcmsSchoolDirectory", key = "{#tenantId, #schoolIds, #allScope, #search, #status, #schoolType, #page, #size}")
    public SchoolDirectoryWorkspace directory(String tenantId, Collection<String> schoolIds, boolean allScope,
            String search, String status, String schoolType, Integer page, Integer size) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 20 : Math.max(1, Math.min(100, size));
        Collection<String> safeIds = schoolIds == null || schoolIds.isEmpty() ? List.of("__none__") : schoolIds;
        Page<SchoolDirectoryRowProjection> result = readRepository.findDirectory(
                tenantId, safeIds, allScope, lower(search), lower(status), upper(schoolType), PageRequest.of(safePage, safeSize));
        return new SchoolDirectoryWorkspace(result.getContent().stream().map(this::directoryItem).toList(),
                safePage, safeSize, result.getTotalElements(), result.getTotalPages(), result.hasNext(), result.hasPrevious());
    }

    @Transactional(readOnly = true)
    public SchoolOperationsWorkspace operations(String tenantId, String schoolId,
            boolean canCreate, boolean canUpdate, boolean canArchive, boolean canManageClasses) {
        return operations(tenantId, schoolId, canCreate, canUpdate, canArchive, canManageClasses,
                java.util.Set.of("academicYears", "grades", "classes", "students", "subjects"));
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "lcmsSchoolOperations",
            sync = true,
            key = "{#tenantId, #schoolId, #canCreate, #canUpdate, #canArchive, #canManageClasses, #selectedFields}"
    )
    public SchoolOperationsWorkspace operations(String tenantId, String schoolId,
            boolean canCreate, boolean canUpdate, boolean canArchive, boolean canManageClasses,
            java.util.Set<String> selectedFields) {
        EducationUnit unit = unitRepository.findByIdAndTenantId(schoolId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found."));
        SchoolOperationalProfile profile = profileRepository.findBySchoolIdAndTenantId(schoolId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("School operational profile not found."));
        java.util.Set<String> selected = selectedFields == null ? java.util.Set.of() : selectedFields;
        boolean includeYears = selected.contains("academicYears");
        boolean includeGrades = selected.contains("grades");
        boolean includeClasses = selected.contains("classes");
        boolean includeSubjects = selected.contains("subjects");
        List<SchoolAcademicYear> years = includeYears
                ? yearRepository.findByTenantIdAndSchoolIdOrderByStartYearDesc(tenantId, schoolId) : List.of();
        List<SchoolGrade> grades = profile.getCurrentAcademicYearId() == null ? List.of()
                : includeGrades ? gradeRepository.findByTenantIdAndSchoolIdAndAcademicYearIdOrderBySortOrderAsc(
                        tenantId, schoolId, profile.getCurrentAcademicYearId()) : List.of();
        List<Classroom> classrooms = includeClasses
                ? bounded(classroomRepository.findByTenantIdAndSchoolIdOrderByNameAsc(
                        tenantId, schoolId, PageRequest.of(0, MAX_WORKSPACE_CLASSES + 1)),
                        MAX_WORKSPACE_CLASSES, "classes")
                : List.of();
        Map<String, List<String>> subjectIdsByClass = subjectIdsByClass(tenantId, schoolId);
        List<CurriculumSubject> subjects = includeSubjects ? bounded(subjectRepository.findAllById(subjectIdsByClass.values().stream()
                        .flatMap(List::stream).distinct().toList()).stream()
                        .filter(subject -> tenantId.equals(subject.getTenantId()))
                        .sorted(java.util.Comparator.comparing(CurriculumSubject::getName)).toList(),
                MAX_WORKSPACE_SUBJECTS, "subjects") : List.of();
        long activeClasses = includeClasses
                ? classrooms.stream().filter(item -> "active".equalsIgnoreCase(item.getStatus())).count()
                : classroomRepository.countByTenantIdAndSchoolIdAndStatusIgnoreCase(tenantId, schoolId, "active");
        long classCount = includeClasses ? classrooms.size() : classroomRepository.countByTenantIdAndSchoolId(tenantId, schoolId);
        long gradeCount = includeGrades ? grades.size()
                : profile.getCurrentAcademicYearId() == null ? 0
                : gradeRepository.countByTenantIdAndSchoolIdAndAcademicYearId(
                        tenantId, schoolId, profile.getCurrentAcademicYearId());
        // Counts come from SQL so a school of any size never loads its students here; the list is paged by students().
        SchoolStudentStats studentStats = studentStats(studentRepository.summarizeSchoolStudents(
                tenantId, schoolId, null, null, null, null, assignedClassIds(subjectIdsByClass)));
        Object[] score = studentRepository.scoreSchoolStudents(tenantId, schoolId).getFirst();
        Map<String, Long> studentsByClass = includeClasses || includeSubjects
                ? studentRepository.countSchoolStudentsByClass(tenantId, schoolId).stream()
                        .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()))
                : Map.of();
        Map<String, Long> studentsBySubject = new java.util.HashMap<>();
        subjectIdsByClass.forEach((classId, subjectIds) -> subjectIds.forEach(subjectId ->
                studentsBySubject.merge(subjectId, studentsByClass.getOrDefault(classId, 0L), Long::sum)));
        return new SchoolOperationsWorkspace(
                new SchoolOperationsProfile(unit.getId(), unit.getName(), unit.getSlug(), profile.getSchoolType(),
                        profile.getOnboardingStatus(), profile.getAddress(), profile.getDistrict(), profile.getPrincipalName(),
                        profile.getContactPhone(), profile.getContactEmail(), profile.getCurrentAcademicYearId(), profile.getVersion()),
                new SchoolOperationsSummary(classCount, activeClasses, studentStats.total(), Math.toIntExact(gradeCount),
                        studentStats, value((Number) score[0]), score[1] == null ? null : ((Number) score[1]).doubleValue()),
                years.stream().map(this::yearResponse).toList(), grades.stream().map(this::gradeResponse).toList(),
                classrooms.stream().map(classroom -> classResponse(classroom, studentsByClass)).toList(),
                List.of(),
                subjects.stream().map(subject -> subjectResponse(subject, studentsBySubject)).toList(),
                new SchoolOperationsPermissions(canCreate, canUpdate, canArchive, canManageClasses));
    }

    /** One page of a school's students, filtered in SQL; stats cover the whole filtered set, not just the page. */
    @Transactional(readOnly = true)
    public SchoolStudentPage students(String tenantId, String schoolId, String search, String grade, String className,
            String status, Integer page, Integer size) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 20 : Math.max(1, Math.min(MAX_STUDENT_PAGE_SIZE, size));
        String like = lower(search) == null ? null : "%" + lower(search) + "%";
        String safeGrade = trimmed(grade);
        String safeClassName = trimmed(className);
        String safeStatus = lower(status);
        Map<String, List<String>> subjectIdsByClass = subjectIdsByClass(tenantId, schoolId);
        Page<Student> result = studentRepository.findSchoolStudents(tenantId, schoolId, like, safeGrade, safeClassName,
                safeStatus, PageRequest.of(safePage, safeSize));
        Map<String, Classroom> classroomById = classroomRepository.findAllById(result.getContent().stream()
                        .map(Student::getAcademicClassId).filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .filter(classroom -> tenantId.equals(classroom.getTenantId()))
                .collect(Collectors.toMap(Classroom::getId, Function.identity()));
        SchoolStudentStats stats = studentStats(studentRepository.summarizeSchoolStudents(tenantId, schoolId, like,
                safeGrade, safeClassName, safeStatus, assignedClassIds(subjectIdsByClass)));
        return new SchoolStudentPage(
                result.getContent().stream().map(student -> studentResponse(student, classroomById, subjectIdsByClass)).toList(),
                safePage, safeSize, result.getTotalElements(), result.getTotalPages(), result.hasNext(), result.hasPrevious(), stats);
    }

    // shortcut: allocations of every school year count; filter by the current year once years carry a "2026-2027" key.
    private Map<String, List<String>> subjectIdsByClass(String tenantId, String schoolId) {
        return allocationRepository.findByTenantIdAndSchoolIdOrderByAcademicClassIdAscSubjectIdAsc(tenantId, schoolId).stream()
                .collect(Collectors.groupingBy(CurriculumClassSubjectAllocation::getAcademicClassId,
                        Collectors.mapping(CurriculumClassSubjectAllocation::getSubjectId,
                                Collectors.collectingAndThen(Collectors.toList(), ids -> ids.stream().distinct().toList()))));
    }

    private Collection<String> assignedClassIds(Map<String, List<String>> subjectIdsByClass) {
        // An empty IN list is invalid SQL; a placeholder id matches no class.
        return subjectIdsByClass.isEmpty() ? List.of("__none__") : subjectIdsByClass.keySet();
    }

    private SchoolStudentStats studentStats(SchoolStudentStatsProjection row) {
        return new SchoolStudentStats(value(row.total()), value(row.atRisk()), value(row.studying()),
                value(row.profileComplete()), value(row.assigned()));
    }

    private SchoolDirectoryItem directoryItem(SchoolDirectoryRowProjection row) {
        String academicYear = row.getAcademicYearStart() == null ? null : row.getAcademicYearStart() + "–" + row.getAcademicYearEnd();
        return new SchoolDirectoryItem(row.getId(), row.getName(), row.getSlug(), row.getSchoolType(), row.getStatus(),
                row.getDistrict(), academicYear, value(row.getClassCount()), value(row.getStudentCount()), value(row.getVersion()));
    }

    private AcademicYearResponse yearResponse(SchoolAcademicYear year) {
        return new AcademicYearResponse(year.getId(), year.getStartYear(), year.getEndYear(), year.getStartYear() + "–" + year.getEndYear(),
                year.getStatus(), year.getStartsOn(), year.getEndsOn(), year.getVersion());
    }

    private GradeResponse gradeResponse(SchoolGrade grade) {
        return new GradeResponse(grade.getId(), grade.getAcademicYearId(), grade.getGradeCode(), grade.getLabel(),
                grade.getSortOrder(), grade.getStatus(), grade.getSource());
    }

    private SchoolClass classResponse(Classroom classroom, Map<String, Long> studentsByClass) {
        long count = studentsByClass.getOrDefault(classroom.getId(), 0L);
        return new SchoolClass(classroom.getId(), classroom.getName(), classroom.getGrade(),
                classroom.getHomeroomTeacherId(), count, classroom.getStatus());
    }

    private SchoolStudent studentResponse(
            Student student, Map<String, Classroom> classroomById, Map<String, List<String>> subjectIdsByClass) {
        Classroom classroom = classroomById.get(student.getAcademicClassId());
        List<String> subjectIds = student.getAcademicClassId() == null ? List.of()
                : subjectIdsByClass.getOrDefault(student.getAcademicClassId(), List.of());
        return new SchoolStudent(student.getId(), student.getStudentCode(), student.getFullName(),
                student.getBirthday() == null ? null : student.getBirthday().toLocalDate().toString(),
                classroom == null ? null : classroom.getGrade(), classroom == null ? null : classroom.getName(),
                student.getParentPhone(), subjectIds, student.getStatus(),
                student.getMetrics() == null ? null : student.getMetrics().getAverageScore());
    }

    private SchoolSubject subjectResponse(CurriculumSubject subject, Map<String, Long> studentsBySubject) {
        long count = studentsBySubject.getOrDefault(subject.getId(), 0L);
        return new SchoolSubject(subject.getId(), subject.getName(), subject.getCode(), count, 1);
    }

    private <T> List<T> bounded(List<T> rows, int maximum, String section) {
        if (rows.size() > maximum) {
            throw new BadRequestException("school operations " + section + " exceeds "
                    + String.format(Locale.ROOT, "%,d", maximum)
                    + " rows; use the paged API for this section");
        }
        return rows;
    }

    private String lower(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    private String trimmed(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String upper(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }
    private long value(Number value) { return value == null ? 0 : value.longValue(); }
}
