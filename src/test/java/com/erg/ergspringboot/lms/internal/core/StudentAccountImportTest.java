package com.erg.ergspringboot.lms.internal.core;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.BulkStudentImportRequest;
import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.CreateStudentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentEnrollmentRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StudentAccountImportTest {

    private final StudentRepository students = mock(StudentRepository.class);
    private final EducationUnitRepository schools = mock(EducationUnitRepository.class);
    private final StudentAccountProvisioner accounts = mock(StudentAccountProvisioner.class);
    private final LmsOperationalCommandService service = new LmsOperationalCommandService(
            mock(ClassroomRepository.class), students,
            mock(StudentEnrollmentRepository.class), schools, mock(UserActivityRepository.class),
            mock(UserRepository.class), accounts, mock(com.erg.ergspringboot.lms.LmsReadCacheInvalidator.class), null);
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
        when(students.findByTenantIdAndSchoolIdAndFullNameIgnoreCase("tenant-1", "school-1", "nguyễn minh trâm anh")).thenReturn(List.of(onRoster));
        when(students.save(any(Student.class))).thenAnswer(call -> call.getArgument(0));
        when(accounts.provisionForImport(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> new StudentAccountProvisioner.ImportedAccount("usr-1", call.getArgument(2)));

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
        when(students.findFirstByTenantIdAndSchoolIdAndStudentCodeIgnoreCase("tenant-1", "school-1", "9A1-2")).thenReturn(Optional.of(withLogin));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(new CreateStudentRequest(
                "9A1-2", "An", "class-1", null, "an.9a1", null, null, null, null,
                null, null, null, null, null, null, "ACTIVE", null, "123456"))), null, access);

        assertThat(result.replayed()).isOne();
        verify(accounts, never()).provisionForImport(any(), any(), any(), any(), any(), any(), any());
        verify(students, never()).save(any());
    }

    @Test
    void aCodeLessRowIsTheStudentWithThatNameAndBirthdayInsteadOfASecondCopy() {
        EducationUnit school = new EducationUnit();
        school.setId("school-1");
        school.setTenantId("tenant-1");
        when(schools.findByIdAndTenantId("school-1", "tenant-1")).thenReturn(Optional.of(school));
        OffsetDateTime birthday = OffsetDateTime.parse("2012-05-10T00:00:00Z");
        Student known = new Student();
        known.setId("student-1");
        known.setSchoolId("school-1");
        known.setAcademicClassId("class-1");
        known.setStudentCode("HS-AAAAAA");
        known.setFullName("Lê Văn Bình");
        known.setBirthday(birthday);
        when(students.findByTenantIdAndSchoolIdAndFullNameIgnoreCase("tenant-1", "school-1", "Lê Văn Bình")).thenReturn(List.of(known));

        // Same child, sheet put in another class: still the same student, not a duplicate.
        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(new CreateStudentRequest(
                "", "Lê Văn Bình", "class-2", null, null, null, birthday, null, null,
                null, null, null, null, null, null, "ACTIVE"))), null, access);

        assertThat(result.replayed()).isOne();
        assertThat(result.items()).extracting("id").containsExactly("student-1");
        verify(students, never()).save(any());
    }

    @Test
    void aCodeLessNewStudentGetsAGeneratedCode() {
        EducationUnit school = new EducationUnit();
        school.setId("school-1");
        school.setTenantId("tenant-1");
        when(schools.findByIdAndTenantId("school-1", "tenant-1")).thenReturn(Optional.of(school));
        when(students.findFirstByTenantIdAndSchoolIdAndStudentCodeIgnoreCase(eq("tenant-1"), eq("school-1"), anyString())).thenReturn(Optional.empty());
        when(students.save(any(Student.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(new CreateStudentRequest(
                null, "Trần An", null, null, null, null, null, null, null,
                null, null, null, null, null, null, "ACTIVE"))), null, access);

        assertThat(result.created()).isOne();
        assertThat(result.items().get(0).studentCode()).matches("HS-[2-9A-HJ-NP-Z]{6}");
    }
}
