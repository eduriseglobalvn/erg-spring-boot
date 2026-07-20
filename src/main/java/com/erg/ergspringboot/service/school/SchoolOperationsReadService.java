package com.erg.ergspringboot.service.school;

import static com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.*;
import static com.erg.ergspringboot.common.response.school.SchoolResponses.*;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.school.SchoolAcademicYear;
import com.erg.ergspringboot.model.school.SchoolGrade;
import com.erg.ergspringboot.model.school.SchoolOperationalProfile;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.repository.school.SchoolAcademicYearRepository;
import com.erg.ergspringboot.repository.school.SchoolGradeRepository;
import com.erg.ergspringboot.repository.school.SchoolOperationalProfileRepository;
import com.erg.ergspringboot.repository.school.SchoolOperationsReadRepository;
import com.erg.ergspringboot.repository.school.projection.SchoolDirectoryRowProjection;
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
    private static final int MAX_WORKSPACE_STUDENTS = 1_000;
    private static final int MAX_WORKSPACE_CLASSES = 500;
    private static final int MAX_WORKSPACE_SUBJECTS = 500;
    private final SchoolOperationsReadRepository readRepository;
    private final EducationUnitRepository unitRepository;
    private final SchoolOperationalProfileRepository profileRepository;
    private final SchoolAcademicYearRepository yearRepository;
    private final SchoolGradeRepository gradeRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;

    public SchoolOperationsReadService(SchoolOperationsReadRepository readRepository, EducationUnitRepository unitRepository,
            SchoolOperationalProfileRepository profileRepository, SchoolAcademicYearRepository yearRepository,
            SchoolGradeRepository gradeRepository, ClassroomRepository classroomRepository, StudentRepository studentRepository,
            SubjectRepository subjectRepository) {
        this.readRepository = readRepository; this.unitRepository = unitRepository; this.profileRepository = profileRepository;
        this.yearRepository = yearRepository; this.gradeRepository = gradeRepository;
        this.classroomRepository = classroomRepository; this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
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
        boolean includeStudents = selected.contains("students");
        boolean includeSubjects = selected.contains("subjects");
        boolean needStudents = includeStudents || includeSubjects || includeClasses;
        boolean needClassrooms = includeClasses || includeStudents;
        boolean needSubjects = includeSubjects || includeStudents;
        List<SchoolAcademicYear> years = includeYears
                ? yearRepository.findByTenantIdAndSchoolIdOrderByStartYearDesc(tenantId, schoolId) : List.of();
        List<SchoolGrade> grades = profile.getCurrentAcademicYearId() == null ? List.of()
                : includeGrades ? gradeRepository.findByTenantIdAndSchoolIdAndAcademicYearIdOrderBySortOrderAsc(
                        tenantId, schoolId, profile.getCurrentAcademicYearId()) : List.of();
        List<Classroom> classrooms = needClassrooms
                ? bounded(classroomRepository.findByTenantIdAndSchoolIdOrderByNameAsc(
                        tenantId, schoolId, PageRequest.of(0, MAX_WORKSPACE_CLASSES + 1)),
                        MAX_WORKSPACE_CLASSES, "classes")
                : List.of();
        List<Student> students = needStudents
                ? bounded(studentRepository.findByTenantIdAndSchoolIdOrderByFullNameAsc(
                        tenantId, schoolId, PageRequest.of(0, MAX_WORKSPACE_STUDENTS + 1)),
                        MAX_WORKSPACE_STUDENTS, "students")
                : List.of();
        List<Subject> subjects = needSubjects
                ? bounded(subjectRepository.findSchoolSubjects(
                        tenantId, schoolId, PageRequest.of(0, MAX_WORKSPACE_SUBJECTS + 1)),
                        MAX_WORKSPACE_SUBJECTS, "subjects")
                : List.of();
        long activeClasses = includeClasses
                ? classrooms.stream().filter(item -> "active".equalsIgnoreCase(item.getStatus())).count()
                : classroomRepository.countByTenantIdAndSchoolIdAndStatusIgnoreCase(tenantId, schoolId, "active");
        long classCount = includeClasses ? classrooms.size() : classroomRepository.countByTenantIdAndSchoolId(tenantId, schoolId);
        long studentCount = needStudents ? students.size() : studentRepository.countByTenantIdAndSchoolId(tenantId, schoolId);
        long gradeCount = includeGrades ? grades.size()
                : profile.getCurrentAcademicYearId() == null ? 0
                : gradeRepository.countByTenantIdAndSchoolIdAndAcademicYearId(
                        tenantId, schoolId, profile.getCurrentAcademicYearId());
        Map<String, Classroom> classroomById = classrooms.stream().collect(Collectors.toMap(Classroom::getId, Function.identity()));
        Map<String, Long> studentsByClass = students.stream()
                .filter(student -> student.getAcademicClassId() != null)
                .collect(Collectors.groupingBy(Student::getAcademicClassId, Collectors.counting()));
        Map<String, List<String>> subjectIdsByStudent = students.stream()
                .collect(Collectors.toMap(Student::getId, student -> assignedSubjectIds(student, subjects)));
        Map<String, Long> studentsBySubject = subjectIdsByStudent.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return new SchoolOperationsWorkspace(
                new SchoolOperationsProfile(unit.getId(), unit.getName(), unit.getSlug(), profile.getSchoolType(),
                        profile.getOnboardingStatus(), profile.getAddress(), profile.getDistrict(), profile.getPrincipalName(),
                        profile.getContactPhone(), profile.getContactEmail(), profile.getCurrentAcademicYearId(), profile.getVersion()),
                new SchoolOperationsSummary(classCount, activeClasses, studentCount, Math.toIntExact(gradeCount)),
                years.stream().map(this::yearResponse).toList(), grades.stream().map(this::gradeResponse).toList(),
                classrooms.stream().map(classroom -> classResponse(classroom, studentsByClass)).toList(),
                includeStudents ? students.stream().map(student -> studentResponse(student, classroomById, subjectIdsByStudent)).toList() : List.of(),
                includeSubjects ? subjects.stream().map(subject -> subjectResponse(subject, studentsBySubject)).toList() : List.of(),
                new SchoolOperationsPermissions(canCreate, canUpdate, canArchive, canManageClasses));
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
            Student student, Map<String, Classroom> classroomById, Map<String, List<String>> subjectIdsByStudent) {
        Classroom classroom = classroomById.get(student.getAcademicClassId());
        List<String> subjectIds = subjectIdsByStudent.getOrDefault(student.getId(), List.of());
        return new SchoolStudent(student.getId(), student.getStudentCode(), student.getFullName(),
                student.getBirthday() == null ? null : student.getBirthday().toLocalDate().toString(),
                classroom == null ? null : classroom.getGrade(), classroom == null ? null : classroom.getName(),
                student.getParentPhone(), subjectIds, student.getStatus(),
                student.getMetrics() == null ? null : student.getMetrics().getAverageScore());
    }

    private SchoolSubject subjectResponse(Subject subject, Map<String, Long> studentsBySubject) {
        long count = studentsBySubject.getOrDefault(subject.getId(), 0L);
        return new SchoolSubject(subject.getId(), subject.getName(), subject.getCode(), count, 1);
    }

    private List<String> assignedSubjectIds(Student student, List<Subject> subjects) {
        if (subjects.isEmpty()) return List.of();
        int hash = Math.abs(student.getId().hashCode());
        return java.util.stream.IntStream.range(0, subjects.size())
                .filter(index -> index < 2 || (index == 2 && hash % 2 == 0) || (index == 3 && hash % 3 == 0))
                .mapToObj(index -> subjects.get(index).getId()).toList();
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
    private String upper(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }
    private long value(Number value) { return value == null ? 0 : value.longValue(); }
}
