package com.erg.ergspringboot.lms.internal.core;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.BulkStudentImportRequest;
import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.CreateStudentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentEnrollmentRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.SubjectRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StudentAccountImportTest {

    private final StudentRepository students = mock(StudentRepository.class);
    private final EducationUnitRepository schools = mock(EducationUnitRepository.class);
    private final StudentAccountProvisioner accounts = mock(StudentAccountProvisioner.class);
    private final LmsOperationalCommandService service = new LmsOperationalCommandService(
            mock(ClassroomRepository.class), students, mock(SubjectRepository.class),
            mock(StudentEnrollmentRepository.class), schools, mock(UserActivityRepository.class),
            mock(UserRepository.class), accounts, mock(com.erg.ergspringboot.lms.LmsReadCacheInvalidator.class));
    private final UserAccessContext access =
            new UserAccessContext("actor-1", "tenant-1", List.of("school-1"), List.of("*"), null, null);

    @Test
    void anAccountImportGivesTheStudentAlreadyOnTheRosterItsLoginInsteadOfADuplicate() {
        EducationUnit school = new EducationUnit();
        school.setId("school-1");
        school.setTenantId("tenant-1");
        when(schools.findByIdAndTenantId("school-1", "tenant-1")).thenReturn(Optional.of(school));
        Student onRoster = new Student();
        onRoster.setId("student-1");
        onRoster.setTenantId("tenant-1");
        onRoster.setSchoolId("school-1");
        onRoster.setAcademicClassId("class-1");
        onRoster.setStudentCode("9A1-2");
        onRoster.setFullName("Nguyễn Minh Trâm Anh");
        when(students.findByTenantIdAndAcademicClassIdOrderByFullNameAsc("tenant-1", "class-1")).thenReturn(List.of(onRoster));
        when(students.save(any(Student.class))).thenAnswer(call -> call.getArgument(0));
        when(accounts.provision(any(), any(), any(), any(), any(), any(), any())).thenReturn("usr-1");

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(new CreateStudentRequest(
                "SCHOOL-1-9A1-2", " nguyễn minh trâm anh ", "class-1", null, "anh.nguyen.9a1", null, null, null, null,
                null, null, null, null, null, null, "ACTIVE", null, "123456"))), null, access);

        assertThat(onRoster.getAuthUserId()).isEqualTo("usr-1");
        assertThat(onRoster.getUsername()).isEqualTo("anh.nguyen.9a1");
        assertThat(result.created()).isOne();
        assertThat(result.replayed()).isZero();
        assertThat(result.items()).hasSize(1);
        verify(students).save(onRoster);
    }

    @Test
    void aStudentThatAlreadyHasALoginIsLeftAlone() {
        EducationUnit school = new EducationUnit();
        school.setId("school-1");
        school.setTenantId("tenant-1");
        when(schools.findByIdAndTenantId("school-1", "tenant-1")).thenReturn(Optional.of(school));
        Student withLogin = new Student();
        withLogin.setId("student-1");
        withLogin.setTenantId("tenant-1");
        withLogin.setSchoolId("school-1");
        withLogin.setStudentCode("9A1-2");
        withLogin.setFullName("An");
        withLogin.setAuthUserId("usr-old");
        when(students.findByTenantIdAndStudentCodeIgnoreCase("tenant-1", "9A1-2")).thenReturn(Optional.of(withLogin));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(new CreateStudentRequest(
                "9A1-2", "An", "class-1", null, "an.9a1", null, null, null, null,
                null, null, null, null, null, null, "ACTIVE", null, "123456"))), null, access);

        assertThat(result.replayed()).isOne();
        verify(accounts, never()).provision(any(), any(), any(), any(), any(), any(), any());
        verify(students, never()).save(any());
    }
}
