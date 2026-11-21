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

    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final EducationUnitRepository schools = mock(EducationUnitRepository.class);
    private final StudentAccountProvisioner accounts = mock(StudentAccountProvisioner.class);
    private final com.erg.ergspringboot.lms.LmsReadCacheInvalidator caches = mock(com.erg.ergspringboot.lms.LmsReadCacheInvalidator.class);
    private final LmsOperationalCommandService service = new LmsOperationalCommandService(
            classrooms, students,
            mock(StudentEnrollmentRepository.class), schools, mock(UserActivityRepository.class),
            mock(UserRepository.class), accounts, caches, null);
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
        onRoster.setStudentCode("HS-AAAAAA");
        onRoster.setFullName("Nguyễn Minh Trâm Anh");
        when(students.findByTenantIdAndSchoolIdAndFullNameNormalized("tenant-1", "school-1", "nguyễn minh trâm anh")).thenReturn(List.of(onRoster));
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
        when(students.findByTenantIdAndSchoolIdAndFullNameNormalized("tenant-1", "school-1", "lê văn bình")).thenReturn(List.of(known));

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
        when(students.saveAndFlush(any(Student.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(new CreateStudentRequest(
                null, "Trần An", null, null, null, null, null, null, null,
                null, null, null, null, null, null, "ACTIVE"))), null, access);

        assertThat(result.created()).isOne();
        assertThat(result.items().get(0).studentCode()).matches("HS-[2-9A-HJ-NP-Z]{6}");
    }

    @Test
    void aBulkImportEvictsEachScopeOnceForTheWholeBatch() {
        givenSchool();
        when(students.findFirstByTenantIdAndSchoolIdAndStudentCodeIgnoreCase(eq("tenant-1"), eq("school-1"), anyString())).thenReturn(Optional.empty());
        List<CreateStudentRequest> rows = List.of(
                new CreateStudentRequest(null, "Trần An", null, null, null, null, null, null, null,
                        null, null, null, null, null, null, "ACTIVE"),
                new CreateStudentRequest(null, "Lê Bình", null, null, null, null, null, null, null,
                        null, null, null, null, null, null, "ACTIVE"));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(rows), null, access);

        assertThat(result.created()).isEqualTo(2);
        verify(caches, org.mockito.Mockito.times(1)).evictScopesAfterCommit(eq("tenant-1"), any());
        verify(caches, org.mockito.Mockito.times(1)).evictAfterCommit(eq("tenant-1"), any());
    }

    private void givenSchool() {
        EducationUnit school = new EducationUnit();
        school.setId("school-1");
        school.setTenantId("tenant-1");
        when(schools.findByIdAndTenantId("school-1", "tenant-1")).thenReturn(Optional.of(school));
        when(students.saveAndFlush(any(Student.class))).thenAnswer(call -> call.getArgument(0));
        com.erg.ergspringboot.lms.persistence.Classroom classroom = new com.erg.ergspringboot.lms.persistence.Classroom();
        classroom.setId("class-1");
        classroom.setSchoolId("school-1");
        when(classrooms.findByTenantIdAndId("tenant-1", "class-1")).thenReturn(Optional.of(classroom));
    }

    private Student onRoster(String id, String code, String name, String status) {
        Student student = new Student();
        student.setId(id);
        student.setSchoolId("school-1");
        student.setAcademicClassId("class-1");
        student.setStudentCode(code);
        student.setFullName(name);
        student.setStatus(status);
        return student;
    }

    private CreateStudentRequest row(String code, String name, OffsetDateTime birthday) {
        return new CreateStudentRequest(code, name, "class-1", null, null, null, birthday, null, null,
                null, null, null, null, null, null, "ACTIVE");
    }

    @Test
    void twoChildrenWithTheSameNameButDifferentCodesAreTwoStudents() {
        givenSchool();
        Student a07 = onRoster("s-a07", "A07", "Nguyễn Văn An", "ENROLLED");
        when(students.findFirstByTenantIdAndSchoolIdAndStudentCodeIgnoreCase("tenant-1", "school-1", "A07")).thenReturn(Optional.of(a07));
        when(students.findByTenantIdAndSchoolIdAndFullNameNormalized("tenant-1", "school-1", "nguyễn văn an")).thenReturn(List.of(a07));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(
                row("A07", "Nguyễn Văn An", null), row("A19", "Nguyễn Văn An", null))), null, access);

        assertThat(result.created()).isOne();
        assertThat(result.replayed()).isOne();
        assertThat(result.items()).extracting("studentCode").containsExactly("A07", "A19");
    }

    @Test
    void aCodeLessRowWithAnotherUnicodeFormAndDoubleSpacesIsStillTheSameStudent() {
        givenSchool();
        Student known = onRoster("s-1", "HS-AAAAAA", "Nguyễn Văn An", "ENROLLED"); // composed form
        String decomposed = java.text.Normalizer.normalize("Nguyễn  Văn An", java.text.Normalizer.Form.NFD);
        when(students.findByTenantIdAndSchoolIdAndFullNameNormalized("tenant-1", "school-1", "nguyễn văn an")).thenReturn(List.of(known));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(row(null, decomposed, null))), null, access);

        assertThat(result.replayed()).isOne();
        assertThat(result.items()).extracting("id").containsExactly("s-1");
    }

    @Test
    void theSameBirthDateInAnotherOffsetIsTheSameChild() {
        givenSchool();
        Student known = onRoster("s-1", "HS-AAAAAA", "Lê Bình", "ENROLLED");
        known.setBirthday(OffsetDateTime.parse("2012-05-10T00:00:00Z"));
        when(students.findByTenantIdAndSchoolIdAndFullNameNormalized("tenant-1", "school-1", "lê bình")).thenReturn(List.of(known));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(
                row(null, "Lê Bình", OffsetDateTime.parse("2012-05-10T00:00:00+07:00")))), null, access);

        assertThat(result.replayed()).isOne();
    }

    @Test
    void anArchivedStudentIsNotSilentlyReplayedByName() {
        givenSchool();
        Student archived = onRoster("s-1", "HS-AAAAAA", "Lê Bình", "ARCHIVED");
        when(students.findByTenantIdAndSchoolIdAndFullNameNormalized("tenant-1", "school-1", "lê bình")).thenReturn(List.of(archived));

        var result = service.importStudents("school-1", new BulkStudentImportRequest(List.of(row(null, "Lê Bình", null))), null, access);

        assertThat(result.created()).isOne();
        assertThat(result.items().get(0).id()).isNotEqualTo("s-1");
    }

    @Test
    void aUniqueCodeCollisionIsAConflictNotAServerError() {
        givenSchool();
        when(students.saveAndFlush(any(Student.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importStudents("school-1",
                new BulkStudentImportRequest(List.of(row("A07", "Trần An", null))), null, access))
                .isInstanceOf(com.erg.ergspringboot.exception.ConflictException.class);
    }
}
