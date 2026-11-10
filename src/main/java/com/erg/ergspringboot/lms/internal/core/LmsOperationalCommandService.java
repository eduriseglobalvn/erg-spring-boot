package com.erg.ergspringboot.lms.internal.core;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.*;
import static com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.*;

import static com.erg.ergspringboot.lms.internal.core.LmsOperationalResponseMapper.*;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.ContentScope;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentEnrollment;
import com.erg.ergspringboot.lms.persistence.Subject;
import com.erg.ergspringboot.users.persistence.UserActivity;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.StudentEnrollmentRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.SubjectRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
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
    private final UserRepository users;
    private final LmsOperationalScopeGuard guard;
    private final StudentAccountProvisioner accounts;


    public LmsOperationalCommandService(
            ClassroomRepository classrooms,
            StudentRepository students,
            SubjectRepository subjects,
            StudentEnrollmentRepository enrollments,
            EducationUnitRepository schools,
            UserActivityRepository activities,
            UserRepository users) {
        this(classrooms, students, subjects, enrollments, schools, activities, users, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    LmsOperationalCommandService(
            ClassroomRepository classrooms,
            StudentRepository students,
            SubjectRepository subjects,
            StudentEnrollmentRepository enrollments,
            EducationUnitRepository schools,
            UserActivityRepository activities,
            UserRepository users,
            StudentAccountProvisioner accounts) {
        this.accounts = accounts;
        this.users = users;
        this.classrooms = classrooms;
        this.students = students;
        this.subjects = subjects;
        this.enrollments = enrollments;
        this.schools = schools;
        this.activities = activities;
        this.guard = new LmsOperationalScopeGuard(classrooms, students, subjects, schools);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsClassesBySchool", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public ClassResponse createClass(String schoolId, CreateClassRequest request, UserAccessContext access) {
        guard.assertSchool(access, schoolId);
        guard.school(access, schoolId);
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
        guard.assertSchool(access, schoolId);
        Classroom classroom = guard.classroom(access, classId);
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
        guard.assertSchool(access, schoolId);
        Classroom classroom = guard.classroom(access, classId);
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
        guard.assertSchool(access, schoolId);
        guard.school(access, schoolId);
        String code = required(request.studentCode(), "Student code");
        if (students.findByTenantIdAndStudentCodeIgnoreCase(access.tenantId(), code).isPresent()) {
            throw new ConflictException("Student code already exists in this tenant.");
        }
        Classroom classroom = request.academicClassId() == null || request.academicClassId().isBlank()
                ? null : guard.scopedClass(access, request.academicClassId(), schoolId);
        Student student = new Student();
        student.setId(IdGenerator.newId());
        student.setTenantId(access.tenantId());
        student.setSchoolId(schoolId);
        student.setAcademicClassId(classroom == null ? null : classroom.getId());
        applyStudent(student, request);
        linkAuthUser(access, student, request.authUserId());
        provisionAccount(access, schoolId, student, request);
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
        guard.assertSchool(access, schoolId);
        Student student = guard.student(access, studentId);
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
        linkAuthUser(access, student, request.authUserId());
        if (request.status() != null) student.setStatus(status(request.status(), student.getStatus()));
        Student saved = students.save(student);
        audit(access, "LMS_STUDENT_UPDATED", saved.getId(), Map.of("schoolId", schoolId));
        return studentResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public StudentResponse archiveStudent(String schoolId, String studentId, Long version, UserAccessContext access) {
        guard.assertSchool(access, schoolId);
        Student student = guard.student(access, studentId);
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
        guard.assertSchool(access, schoolId);
        guard.school(access, schoolId);
        String key = normalizeKey(idempotencyKey);
        List<StudentResponse> result = new ArrayList<>();
        int replayed = 0;
        for (CreateStudentRequest item : request.items()) {
            var existing = students.findByTenantIdAndStudentCodeIgnoreCase(access.tenantId(), item.studentCode().trim());
            if (existing.isPresent()
                    && !normalize(existing.get().getFullName()).equals(normalize(item.fullName()))) {
                throw new ConflictException("Import student code belongs to a different student.");
            }
            // An account import for a student already on the roster (same class + name, imported earlier without
            // a login) gives that student its account instead of creating a duplicate.
            if (existing.isEmpty() && trim(item.password()) != null) {
                existing = unlinkedClassmate(schoolId, item, access);
            }
            if (existing.isPresent()) {
                Student student = existing.get();
                if (trim(item.password()) != null && student.getAuthUserId() == null) {
                    result.add(studentResponse(attachAccount(access, schoolId, student, item)));
                    continue;
                }
                result.add(studentResponse(student));
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
            guard.assertSchool(access, schoolId);
            guard.school(access, schoolId);
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
        Subject subject = guard.subject(access, subjectId);
        guard.assertSubjectScope(access, subject);
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
        Subject subject = guard.subject(access, subjectId);
        guard.assertSubjectScope(access, subject);
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
        Student student = guard.student(access, request.studentId());
        Classroom classroom = guard.scopedClass(access, request.classId(), student.getSchoolId());
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


    private void assertVersion(Long requested, Long actual, String resource) {
        if (requested == null) {
            throw new BadRequestException(resource + " version is required.");
        }
        if (!Objects.equals(requested, actual == null ? 0L : actual)) {
            throw new ConflictException(resource + " has been updated by another request.");
        }
    }

    /**
     * B2: links the LMS student to its login account so learner flows can resolve Student by authUserId.
     * The account must exist in the caller's tenant and must not already be linked to another student.
     */
    private void linkAuthUser(UserAccessContext access, Student student, String requestedUserId) {
        String authUserId = trim(requestedUserId);
        if (authUserId == null || authUserId.equals(student.getAuthUserId())) return;
        if (users.findByIdAndTenantId(authUserId, access.tenantId()).isEmpty()) {
            throw new BadRequestException("Login account does not exist in this tenant.");
        }
        students.findFirstByTenantIdAndAuthUserId(access.tenantId(), authUserId)
                .filter(existing -> !existing.getId().equals(student.getId()))
                .ifPresent(existing -> { throw new ConflictException("Login account is already linked to another student."); });
        student.setAuthUserId(authUserId);
    }

    private java.util.Optional<Student> unlinkedClassmate(String schoolId, CreateStudentRequest item, UserAccessContext access) {
        String classId = trim(item.academicClassId());
        if (classId == null) return java.util.Optional.empty();
        return students.findByTenantIdAndAcademicClassIdOrderByFullNameAsc(access.tenantId(), classId).stream()
                .filter(student -> schoolId.equals(student.getSchoolId()) && student.getAuthUserId() == null)
                .filter(student -> normalize(student.getFullName()).equals(normalize(item.fullName())))
                .findFirst();
    }

    private Student attachAccount(UserAccessContext access, String schoolId, Student student, CreateStudentRequest item) {
        if (accounts == null) throw new BadRequestException("Student account creation is not available.");
        student.setAuthUserId(accounts.provision(access, schoolId, trim(item.username()), trim(item.email()),
                student.getFullName(), trim(item.phone()), item.password()));
        if (trim(student.getUsername()) == null) student.setUsername(trim(item.username()));
        Student saved = students.save(student);
        audit(access, "LMS_STUDENT_ACCOUNT_CREATED", saved.getId(), Map.of("schoolId", schoolId));
        return saved;
    }

    private void provisionAccount(UserAccessContext access, String schoolId, Student student, CreateStudentRequest request) {
        if (trim(request.password()) == null || student.getAuthUserId() != null) return;
        if (accounts == null) throw new BadRequestException("Student account creation is not available.");
        student.setAuthUserId(accounts.provision(access, schoolId, trim(request.username()), trim(request.email()),
                trim(request.fullName()), trim(request.phone()), request.password()));
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


    private void audit(UserAccessContext access, String action, String resourceId, Map<String, Object> metadata) {
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId()); activity.setUserId(access.userId()); activity.setTenantId(access.tenantId());
        activity.setAction(action); activity.setDescription("LMS operational mutation"); activity.setMetadata(metadata);
        activity.setCreatedAt(OffsetDateTime.now()); activities.save(activity);
    }

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
