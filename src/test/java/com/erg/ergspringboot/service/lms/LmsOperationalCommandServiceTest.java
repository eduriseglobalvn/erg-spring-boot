package com.erg.ergspringboot.service.lms;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.StudentEnrollment;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentEnrollmentRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LmsOperationalCommandServiceTest {

    @Mock private ClassroomRepository classrooms;
    @Mock private StudentRepository students;
    @Mock private SubjectRepository subjects;
    @Mock private StudentEnrollmentRepository enrollments;
    @Mock private EducationUnitRepository schools;
    @Mock private UserActivityRepository activities;

    private LmsOperationalCommandService service;
    private final UserAccessContext access =
            new UserAccessContext("actor-1", "tenant-1", List.of("school-1"), List.of("*"), null, null);

    @BeforeEach
    void setUp() {
        service = new LmsOperationalCommandService(classrooms, students, subjects, enrollments, schools, activities);
    }

    @Test
    void createsClassOnlyInsideManagedTenantAndSchool() {
        when(schools.findByIdAndTenantId("school-1", "tenant-1")).thenReturn(Optional.of(school()));
        when(classrooms.existsByTenantIdAndSchoolIdAndNameIgnoreCaseAndAcademicYear(
                "tenant-1", "school-1", "10A", "2026")).thenReturn(false);
        when(classrooms.save(any(Classroom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createClass("school-1", new CreateClassRequest("10A", "10", "2026", null, "ACTIVE"), access);

        assertThat(result.tenantId()).isEqualTo("tenant-1");
        assertThat(result.schoolId()).isEqualTo("school-1");
        assertThat(result.version()).isZero();
        verify(classrooms).save(any(Classroom.class));
    }

    @Test
    void rejectsSchoolOutsideAuthenticatedScopeBeforePersistence() {
        UserAccessContext scoped = new UserAccessContext("actor-1", "tenant-1", List.of("school-1"), List.of(), null, null);

        assertThatThrownBy(() -> service.createClass(
                "school-2", new CreateClassRequest("10A", null, null, null, null), scoped))
                .isInstanceOf(ForbiddenException.class);
        verify(classrooms, never()).save(any());
        verify(schools, never()).findByIdAndTenantId(anyString(), anyString());
    }

    @Test
    void rejectsStaleClassVersionAndRequiresVersionOnUpdate() {
        Classroom classroom = classroom("class-1", 4L);
        when(classrooms.findByTenantIdAndId("tenant-1", "class-1")).thenReturn(Optional.of(classroom));

        assertThatThrownBy(() -> service.updateClass(
                "school-1", "class-1", new UpdateClassRequest(3L, null, null, null, null, null), access))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.updateClass(
                "school-1", "class-1", new UpdateClassRequest(null, null, null, null, null, null), access))
                .isInstanceOf(BadRequestException.class);
        verify(classrooms, never()).save(any());
    }

    @Test
    void repeatedImportWithTheSameStudentCodesIsReplaySafe() {
        Student existing = student("student-1", "S-001", "Alice");
        when(schools.findByIdAndTenantId("school-1", "tenant-1")).thenReturn(Optional.of(school()));
        when(students.findByTenantIdAndStudentCodeIgnoreCase("tenant-1", "S-001"))
                .thenReturn(Optional.of(existing));

        var result = service.importStudents("school-1",
                new BulkStudentImportRequest(List.of(new CreateStudentRequest(
                        "S-001", "Alice", null, null, null, null, null, null, null, null,
                        null, null, null, null, null, "ENROLLED"))),
                "batch-1", access);

        assertThat(result.idempotencyKey()).isEqualTo("batch-1");
        assertThat(result.created()).isZero();
        assertThat(result.replayed()).isOne();
        verify(students, never()).save(any());
    }

    @Test
    void enrollmentReplayUsesTenantScopedIdempotencyKey() {
        StudentEnrollment replay = new StudentEnrollment();
        replay.setId("enrollment-1");
        replay.setTenantId("tenant-1");
        replay.setSchoolId("school-1");
        replay.setStudentId("student-1");
        replay.setClassId("class-1");
        replay.setStatus("ENROLLED");
        replay.setIdempotencyKey("enroll-1");
        when(enrollments.findByTenantIdAndIdempotencyKey("tenant-1", "enroll-1"))
                .thenReturn(Optional.of(replay));

        var result = service.enroll(new EnrollStudentRequest("student-1", "class-1", "2026"), "enroll-1", access);

        assertThat(result.replayed()).isTrue();
        assertThat(result.id()).isEqualTo("enrollment-1");
        verify(enrollments, never()).save(any());
        verify(students, never()).save(any());
    }

    private EducationUnit school() {
        EducationUnit value = new EducationUnit();
        value.setId("school-1");
        value.setTenantId("tenant-1");
        return value;
    }

    private Classroom classroom(String id, long version) {
        Classroom value = new Classroom();
        value.setId(id);
        value.setTenantId("tenant-1");
        value.setSchoolId("school-1");
        value.setName("10A");
        value.setRecordVersion(version);
        return value;
    }

    private Student student(String id, String code, String name) {
        Student value = new Student();
        value.setId(id);
        value.setTenantId("tenant-1");
        value.setSchoolId("school-1");
        value.setStudentCode(code);
        value.setFullName(name);
        return value;
    }
}
