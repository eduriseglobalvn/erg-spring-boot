package com.erg.ergspringboot.service.school;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.school.SchoolOperationalProfile;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.repository.school.SchoolAcademicYearRepository;
import com.erg.ergspringboot.repository.school.SchoolGradeRepository;
import com.erg.ergspringboot.repository.school.SchoolOperationalProfileRepository;
import com.erg.ergspringboot.repository.school.SchoolOperationsReadRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class SchoolOperationsReadServiceTest {
    @Mock private SchoolOperationsReadRepository readRepository;
    @Mock private EducationUnitRepository unitRepository;
    @Mock private SchoolOperationalProfileRepository profileRepository;
    @Mock private SchoolAcademicYearRepository yearRepository;
    @Mock private SchoolGradeRepository gradeRepository;
    @Mock private ClassroomRepository classroomRepository;
    @Mock private SubjectRepository subjectRepository;
    private StudentRepository studentRepository;
    private SchoolOperationsReadService service;

    @BeforeEach
    void setUp() {
        List<Student> oversized = IntStream.range(0, 1_001).mapToObj(index -> {
            Student student = new Student();
            student.setId("student-" + index);
            student.setTenantId("tenant-a");
            student.setSchoolId("school-a");
            return student;
        }).toList();
        studentRepository = mock(StudentRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("findByTenantIdAndSchoolIdOrderByFullNameAsc")) {
                return oversized;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        service = new SchoolOperationsReadService(readRepository, unitRepository, profileRepository,
                yearRepository, gradeRepository, classroomRepository, studentRepository, subjectRepository);

        EducationUnit unit = new EducationUnit();
        unit.setId("school-a");
        unit.setTenantId("tenant-a");
        SchoolOperationalProfile profile = new SchoolOperationalProfile();
        profile.setSchoolId("school-a");
        profile.setTenantId("tenant-a");
        when(unitRepository.findByIdAndTenantId("school-a", "tenant-a")).thenReturn(Optional.of(unit));
        when(profileRepository.findBySchoolIdAndTenantId("school-a", "tenant-a")).thenReturn(Optional.of(profile));
    }

    @Test
    void rejectsStudentSectionAboveBoundInsteadOfMaterializingUnboundedWorkspace() {
        assertThatThrownBy(() -> service.operations(
                "tenant-a", "school-a", true, true, true, true, Set.of("students")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1,000")
                .hasMessageContaining("paged");
    }

    @Test
    void summaryKeepsGradeCountWhenGradeRowsAreNotSelected() {
        SchoolOperationalProfile profile = profileRepository.findBySchoolIdAndTenantId("school-a", "tenant-a").orElseThrow();
        profile.setCurrentAcademicYearId("year-a");
        when(gradeRepository.countByTenantIdAndSchoolIdAndAcademicYearId("tenant-a", "school-a", "year-a"))
                .thenReturn(12L);

        var response = service.operations(
                "tenant-a", "school-a", true, true, true, true, Set.of());

        assertThat(response.summary().gradeCount()).isEqualTo(12);
        assertThat(response.grades()).isEmpty();
    }
}
