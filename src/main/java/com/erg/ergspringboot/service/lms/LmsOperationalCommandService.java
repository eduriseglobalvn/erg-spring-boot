package com.erg.ergspringboot.service.lms;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.*;
import static com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.*;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.ContentScope;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.StudentEnrollment;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentEnrollmentRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the write side of the Admin LMS operational directory. The service deliberately
 * derives tenant, actor and school ownership from {@link UserAccessContext}; no request
 * payload can widen the authenticated scope.
 */
@Service
public class LmsOperationalCommandService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED", "DRAFT", "ENROLLED");

    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final StudentEnrollmentRepository enrollments;
    private final EducationUnitRepository schools;
    private final UserActivityRepository activities;

    public LmsOperationalCommandService(
            ClassroomRepository classrooms,
            StudentRepository students,
            SubjectRepository subjects,
            StudentEnrollmentRepository enrollments,
            EducationUnitRepository schools,
            UserActivityRepository activities) {
        this.classrooms = classrooms;
        this.students = students;
        this.subjects = subjects;
        this.enrollments = enrollments;
        this.schools = schools;
        this.activities = activities;
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsClassesBySchool", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public ClassResponse createClass(String schoolId, CreateClassRequest request, UserAccessContext access) {
        assertSchool(access, schoolId);
        school(access, schoolId);
        String name = required(request.name(), "Class name");
        String year = trim(request.academicYear());
        if (classrooms.existsByTenantIdAndSchoolIdAndNameIgnoreCaseAndAcademicYear(
                access.tenantId(), schoolId, name, year)) {
            throw new ConflictException("A class with the same name already exists in this academic year.");
        }
        Classroom classroom = new Classroom();
        classroom.setId(IdGenerator.newId());
        classroom.setTenantId(access.tenantId());
        classroom.setSchoolId(schoolId);
        classroom.setName(name);
        classroom.setGrade(trim(request.grade()));
        classroom.setAcademicYear(year);
        classroom.setHomeroomTeacherId(trim(request.homeroomTeacherId()));
        classroom.setStatus(status(request.status(), "ACTIVE"));
        Classroom saved = classrooms.save(classroom);
        audit(access, "LMS_CLASS_CREATED", saved.getId(), Map.of("schoolId", schoolId));
        return classResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsClassesBySchool", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public ClassResponse updateClass(String schoolId, String classId, UpdateClassRequest request,
                                     UserAccessContext access) {
        assertSchool(access, schoolId);
        Classroom classroom = classroom(access, classId);
        if (!schoolId.equals(classroom.getSchoolId())) throw new ResourceNotFoundException("Class not found.");
        assertVersion(request.version(), classroom.getRecordVersion(), "Class");
        String nextName = request.name() == null ? classroom.getName() : required(request.name(), "Class name");
        String nextYear = request.academicYear() == null ? classroom.getAcademicYear() : trim(request.academicYear());
        if ((!Objects.equals(normalize(nextName), normalize(classroom.getName()))
                || !Objects.equals(normalize(nextYear), normalize(classroom.getAcademicYear())))
                && classrooms.existsByTenantIdAndSchoolIdAndNameIgnoreCaseAndAcademicYear(
                        access.tenantId(), schoolId, nextName, nextYear)) {
            throw new ConflictException("A class with the same name already exists in this academic year.");
        }
        classroom.setName(nextName);
        if (request.grade() != null) classroom.setGrade(trim(request.grade()));
        if (request.academicYear() != null) classroom.setAcademicYear(nextYear);
        if (request.homeroomTeacherId() != null) classroom.setHomeroomTeacherId(trim(request.homeroomTeacherId()));
        if (request.status() != null) classroom.setStatus(status(request.status(), classroom.getStatus()));
        Classroom saved = classrooms.save(classroom);
        audit(access, "LMS_CLASS_UPDATED", saved.getId(), Map.of("schoolId", schoolId));
        return classResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsClassesBySchool", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public ClassResponse archiveClass(String schoolId, String classId, Long version, UserAccessContext access) {
        assertSchool(access, schoolId);
        Classroom classroom = classroom(access, classId);
        if (!schoolId.equals(classroom.getSchoolId())) throw new ResourceNotFoundException("Class not found.");
        assertVersion(version, classroom.getRecordVersion(), "Class");
        classroom.setStatus("ARCHIVED");
        Classroom saved = classrooms.save(classroom);
        audit(access, "LMS_CLASS_ARCHIVED", saved.getId(), Map.of("schoolId", schoolId));
        return classResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public StudentResponse createStudent(String schoolId, CreateStudentRequest request, UserAccessContext access) {
        assertSchool(access, schoolId);
        school(access, schoolId);
        String code = required(request.studentCode(), "Student code");
        if (students.findByTenantIdAndStudentCodeIgnoreCase(access.tenantId(), code).isPresent()) {
            throw new ConflictException("Student code already exists in this tenant.");
        }
        Classroom classroom = request.academicClassId() == null || request.academicClassId().isBlank()
                ? null : scopedClass(access, request.academicClassId(), schoolId);
        Student student = new Student();
        student.setId(IdGenerator.newId());
        student.setTenantId(access.tenantId());
        student.setSchoolId(schoolId);
        student.setAcademicClassId(classroom == null ? null : classroom.getId());
        applyStudent(student, request);
        student.setStudentCode(code);
        student.setFullName(required(request.fullName(), "Student name"));
        student.setStatus(status(request.status(), "ENROLLED"));
        Student saved = students.save(student);
        audit(access, "LMS_STUDENT_CREATED", saved.getId(), Map.of("schoolId", schoolId));
        return studentResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public StudentResponse updateStudent(String schoolId, String studentId, UpdateStudentRequest request,
                                         UserAccessContext access) {
        assertSchool(access, schoolId);
        Student student = student(access, studentId);
        if (!schoolId.equals(student.getSchoolId())) throw new ResourceNotFoundException("Student not found.");
        assertVersion(request.version(), student.getRecordVersion(), "Student");
        if (request.studentCode() != null && !normalize(request.studentCode()).equals(normalize(student.getStudentCode()))) {
            students.findByTenantIdAndStudentCodeIgnoreCase(access.tenantId(), request.studentCode().trim())
                    .filter(existing -> !existing.getId().equals(studentId))
                    .ifPresent(existing -> { throw new ConflictException("Student code already exists in this tenant."); });
            student.setStudentCode(required(request.studentCode(), "Student code"));
        }
        if (request.fullName() != null) student.setFullName(required(request.fullName(), "Student name"));
        if (request.academicClassId() != null && !Objects.equals(request.academicClassId(), student.getAcademicClassId())) {
            throw new ConflictException("Move the student through the enrollment command.");
        }
        applyStudent(student, request);
        if (request.status() != null) student.setStatus(status(request.status(), student.getStatus()));
        Student saved = students.save(student);
        audit(access, "LMS_STUDENT_UPDATED", saved.getId(), Map.of("schoolId", schoolId));
        return studentResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public StudentResponse archiveStudent(String schoolId, String studentId, Long version, UserAccessContext access) {
        assertSchool(access, schoolId);
        Student student = student(access, studentId);
        if (!schoolId.equals(student.getSchoolId())) throw new ResourceNotFoundException("Student not found.");
        assertVersion(version, student.getRecordVersion(), "Student");
        student.setStatus("ARCHIVED");
        Student saved = students.save(student);
        audit(access, "LMS_STUDENT_ARCHIVED", saved.getId(), Map.of("schoolId", schoolId));
        return studentResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public BulkStudentImportResponse importStudents(String schoolId, BulkStudentImportRequest request,
                                                    String idempotencyKey, UserAccessContext access) {
        assertSchool(access, schoolId);
        school(access, schoolId);
        String key = normalizeKey(idempotencyKey);
        List<StudentResponse> result = new ArrayList<>();
        int replayed = 0;
        for (CreateStudentRequest item : request.items()) {
            var existing = students.findByTenantIdAndStudentCodeIgnoreCase(access.tenantId(), item.studentCode().trim());
            if (existing.isPresent()) {
                if (!normalize(existing.get().getFullName()).equals(normalize(item.fullName()))) {
                    throw new ConflictException("Import student code belongs to a different student.");
                }
                result.add(studentResponse(existing.get()));
                replayed++;
                continue;
            }
            result.add(createStudent(schoolId, item, access));
        }
        audit(access, "LMS_STUDENT_IMPORT", schoolId, Map.of("requested", request.items().size(), "idempotencyKey", key == null ? "none" : key));
        return new BulkStudentImportResponse(key, request.items().size(), request.items().size() - replayed, replayed, result);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsSchoolSubjects", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public SubjectResponse createSubject(CreateSubjectRequest request, UserAccessContext access) {
        String schoolId = trim(request.schoolId());
        if (schoolId != null) {
            assertSchool(access, schoolId);
            school(access, schoolId);
        }
        String code = required(request.code(), "Subject code");
        if (subjects.findByTenantIdAndCodeIgnoreCase(access.tenantId(), code).isPresent()) {
            throw new ConflictException("Subject code already exists in this tenant.");
        }
        Subject subject = new Subject();
        subject.setId(IdGenerator.newId());
        subject.setTenantId(access.tenantId());
        subject.setScope(new ContentScope(schoolId == null ? "tenant" : "school", schoolId));
        subject.setName(required(request.name(), "Subject name"));
        subject.setCode(code);
        subject.setStatus(status(request.status(), "ACTIVE"));
        Subject saved = subjects.save(subject);
        audit(access, "LMS_SUBJECT_CREATED", saved.getId(), Map.of("schoolId", schoolId == null ? "tenant" : schoolId));
        return subjectResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsSchoolSubjects", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public SubjectResponse updateSubject(String subjectId, UpdateSubjectRequest request, UserAccessContext access) {
        Subject subject = subject(access, subjectId);
        assertSubjectScope(access, subject);
        assertVersion(request.version(), subject.getRecordVersion(), "Subject");
        if (request.code() != null && !normalize(request.code()).equals(normalize(subject.getCode()))) {
            subjects.findByTenantIdAndCodeIgnoreCase(access.tenantId(), request.code().trim())
                    .filter(existing -> !existing.getId().equals(subjectId))
                    .ifPresent(existing -> { throw new ConflictException("Subject code already exists in this tenant."); });
            subject.setCode(required(request.code(), "Subject code"));
        }
        if (request.name() != null) subject.setName(required(request.name(), "Subject name"));
        if (request.status() != null) subject.setStatus(status(request.status(), subject.getStatus()));
        Subject saved = subjects.save(subject);
        audit(access, "LMS_SUBJECT_UPDATED", saved.getId(), Map.of("scope", "tenant"));
        return subjectResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsSchoolSubjects", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public SubjectResponse archiveSubject(String subjectId, Long version, UserAccessContext access) {
        Subject subject = subject(access, subjectId);
        assertSubjectScope(access, subject);
        assertVersion(version, subject.getRecordVersion(), "Subject");
        subject.setStatus("ARCHIVED");
        Subject saved = subjects.save(subject);
        audit(access, "LMS_SUBJECT_ARCHIVED", saved.getId(), Map.of("scope", "tenant"));
        return subjectResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsClassesBySchool", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public EnrollmentResponse enroll(EnrollStudentRequest request, String idempotencyKey, UserAccessContext access) {
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            var replay = enrollments.findByTenantIdAndIdempotencyKey(access.tenantId(), key);
            if (replay.isPresent()) return enrollmentResponse(replay.get(), true);
        }
        Student student = student(access, request.studentId());
        Classroom classroom = scopedClass(access, request.classId(), student.getSchoolId());
        String year = trim(request.academicYear());
        var existing = enrollments.findByTenantIdAndStudentIdAndClassIdAndAcademicYear(
                access.tenantId(), student.getId(), classroom.getId(), year);
        if (existing.isPresent() && "ENROLLED".equals(existing.get().getStatus())) {
            throw new ConflictException("Student is already enrolled in this class.");
        }
        StudentEnrollment enrollment = existing.orElseGet(StudentEnrollment::new);
        enrollment.setId(enrollment.getId() == null ? IdGenerator.newId() : enrollment.getId());
        enrollment.setTenantId(access.tenantId());
        enrollment.setSchoolId(student.getSchoolId());
        enrollment.setStudentId(student.getId());
        enrollment.setClassId(classroom.getId());
        enrollment.setAcademicYear(year);
        enrollment.setStatus("ENROLLED");
        enrollment.setIdempotencyKey(key);
        enrollment.setCreatedBy(access.userId());
        StudentEnrollment saved = enrollments.save(enrollment);
        student.setAcademicClassId(classroom.getId());
        student.setStatus("ENROLLED");
        students.save(student);
        audit(access, "LMS_STUDENT_ENROLLED", student.getId(), Map.of("classId", classroom.getId(), "schoolId", student.getSchoolId()));
        return enrollmentResponse(saved, false);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsClassesBySchool", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public BulkEnrollmentResponse bulkEnroll(BulkEnrollRequest request, String idempotencyKey,
                                             UserAccessContext access) {
        String root = normalizeKey(idempotencyKey);
        List<EnrollmentResponse> results = new ArrayList<>();
        int replayed = 0;
        for (EnrollmentItem item : request.items()) {
            String itemKey = root == null ? null : root + ":" + item.studentId() + ":" + item.classId();
            EnrollmentResponse response = enroll(new EnrollStudentRequest(item.studentId(), item.classId(), item.academicYear()), itemKey, access);
            replayed += response.replayed() ? 1 : 0;
            results.add(response);
        }
        return new BulkEnrollmentResponse(root, request.items().size(), request.items().size() - replayed, replayed, results);
    }

    private Classroom scopedClass(UserAccessContext access, String classId, String schoolId) {
        Classroom classroom = classroom(access, classId);
        if (!Objects.equals(classroom.getSchoolId(), schoolId)) throw new BadRequestException("Class does not belong to the requested school.");
        assertSchool(access, schoolId);
        return classroom;
    }

    private Classroom classroom(UserAccessContext access, String id) {
        return classrooms.findByTenantIdAndId(access.tenantId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found."));
    }

    private Student student(UserAccessContext access, String id) {
        return students.findByTenantIdAndId(access.tenantId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
    }

    private Subject subject(UserAccessContext access, String id) {
        return subjects.findById(id).filter(value -> access.tenantId().equals(value.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found."));
    }

    private EducationUnit school(UserAccessContext access, String id) {
        return schools.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found."));
    }

    private void assertSchool(UserAccessContext access, String schoolId) {
        if (schoolId == null || schoolId.isBlank() || !access.canAccessEducationUnit(schoolId)) {
            throw new ForbiddenException("School scope is not allowed for the authenticated principal.");
        }
    }

    private void assertSubjectScope(UserAccessContext access, Subject subject) {
        ContentScope scope = subject.getScope();
        if (scope != null && "school".equalsIgnoreCase(scope.getType()) && scope.getSchoolId() != null) {
            assertSchool(access, scope.getSchoolId());
        }
    }

    private void assertVersion(Long requested, Long actual, String resource) {
        if (requested == null) {
            throw new BadRequestException(resource + " version is required.");
        }
        if (!Objects.equals(requested, actual == null ? 0L : actual)) {
            throw new ConflictException(resource + " has been updated by another request.");
        }
    }

    private void applyStudent(Student student, CreateStudentRequest request) {
        student.setEmail(trim(request.email())); student.setUsername(trim(request.username())); student.setGender(trim(request.gender()));
        student.setBirthday(request.birthday()); student.setPhone(trim(request.phone())); student.setAddress(trim(request.address()));
        student.setParentName(trim(request.parentName())); student.setParentPhone(trim(request.parentPhone())); student.setParentEmail(trim(request.parentEmail()));
        student.setParentRelationship(trim(request.parentRelationship())); student.setEnrollmentDate(request.enrollmentDate()); student.setNote(trim(request.note()));
    }

    private void applyStudent(Student student, UpdateStudentRequest request) {
        if (request.email() != null) student.setEmail(trim(request.email())); if (request.username() != null) student.setUsername(trim(request.username()));
        if (request.gender() != null) student.setGender(trim(request.gender())); if (request.birthday() != null) student.setBirthday(request.birthday());
        if (request.phone() != null) student.setPhone(trim(request.phone())); if (request.address() != null) student.setAddress(trim(request.address()));
        if (request.parentName() != null) student.setParentName(trim(request.parentName())); if (request.parentPhone() != null) student.setParentPhone(trim(request.parentPhone()));
        if (request.parentEmail() != null) student.setParentEmail(trim(request.parentEmail())); if (request.parentRelationship() != null) student.setParentRelationship(trim(request.parentRelationship()));
        if (request.enrollmentDate() != null) student.setEnrollmentDate(request.enrollmentDate()); if (request.note() != null) student.setNote(trim(request.note()));
    }

    private ClassResponse classResponse(Classroom value) {
        return new ClassResponse(value.getId(), value.getTenantId(), value.getSchoolId(), value.getName(), value.getGrade(), value.getAcademicYear(), value.getHomeroomTeacherId(), value.getStatus(), version(value.getRecordVersion()), value.getUpdatedAt());
    }

    private StudentResponse studentResponse(Student value) {
        return new StudentResponse(value.getId(), value.getTenantId(), value.getSchoolId(), value.getAcademicClassId(), value.getStudentCode(), value.getFullName(), value.getUsername(), value.getAuthUserId(), value.getEmail(), value.getGender(), value.getBirthday(), value.getPhone(), value.getAddress(), value.getParentName(), value.getParentPhone(), value.getParentEmail(), value.getParentRelationship(), value.getEnrollmentDate(), value.getNote(), value.getStatus(), version(value.getRecordVersion()), value.getUpdatedAt());
    }

    private SubjectResponse subjectResponse(Subject value) {
        String schoolId = value.getScope() == null ? null : value.getScope().getSchoolId();
        return new SubjectResponse(value.getId(), value.getTenantId(), schoolId, value.getName(), value.getCode(), value.getStatus(), version(value.getRecordVersion()), value.getUpdatedAt());
    }

    private EnrollmentResponse enrollmentResponse(StudentEnrollment value, boolean replayed) {
        return new EnrollmentResponse(value.getId(), value.getTenantId(), value.getSchoolId(), value.getStudentId(), value.getClassId(), value.getAcademicYear(), value.getStatus(), version(value.getRecordVersion()), replayed, value.getUpdatedAt());
    }

    private void audit(UserAccessContext access, String action, String resourceId, Map<String, Object> metadata) {
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId()); activity.setUserId(access.userId()); activity.setTenantId(access.tenantId());
        activity.setAction(action); activity.setDescription("LMS operational mutation"); activity.setMetadata(metadata);
        activity.setCreatedAt(OffsetDateTime.now()); activities.save(activity);
    }

    private long version(Long value) { return value == null ? 0L : value; }
    private String status(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE_STATUSES.contains(normalized)) throw new BadRequestException("Unsupported status.");
        return normalized;
    }
    private String required(String value, String name) { if (value == null || value.isBlank()) throw new BadRequestException(name + " is required."); return value.trim(); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String normalizeKey(String value) { String key = trim(value); return key == null ? null : key.length() > 255 ? key.substring(0, 255) : key; }
}
