package com.erg.ergspringboot.school.internal.read;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.school.persistence.SchoolOperationalProfile;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocation;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocationRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubject;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubjectRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import com.erg.ergspringboot.school.persistence.SchoolAcademicYearRepository;
import com.erg.ergspringboot.school.persistence.SchoolGradeRepository;
import com.erg.ergspringboot.school.persistence.SchoolOperationalProfileRepository;
import com.erg.ergspringboot.school.persistence.SchoolOperationsReadRepository;
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
    @Mock private CurriculumSubjectRepository subjectRepository;
    @Mock private CurriculumClassSubjectAllocationRepository allocationRepository;
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
                yearRepository, gradeRepository, classroomRepository, studentRepository, subjectRepository, allocationRepository);

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

    @Test
    void subjectsComeFromClassAllocationsAndStudentsInheritTheirClassSubjects() {
        StudentRepository students = mock(StudentRepository.class);
        Student inClass = new Student();
        inClass.setId("s1");
        inClass.setAcademicClassId("class-1");
        Student otherClass = new Student();
        otherClass.setId("s2");
        otherClass.setAcademicClassId("class-2");
        when(students.findByTenantIdAndSchoolIdOrderByFullNameAsc(eq("tenant-a"), eq("school-a"), any()))
                .thenReturn(List.of(inClass, otherClass));
        CurriculumClassSubjectAllocation allocation = new CurriculumClassSubjectAllocation();
        allocation.setAcademicClassId("class-1");
        allocation.setSubjectId("sub-ic3");
        when(allocationRepository.findByTenantIdAndSchoolIdOrderByAcademicClassIdAscSubjectIdAsc("tenant-a", "school-a"))
                .thenReturn(List.of(allocation));
        CurriculumSubject subject = new CurriculumSubject();
        subject.setId("sub-ic3");
        subject.setTenantId("tenant-a");
        subject.setName("IC3");
        subject.setCode("ic3");
        when(subjectRepository.findAllById(List.of("sub-ic3"))).thenReturn(List.of(subject));
        SchoolOperationsReadService small = new SchoolOperationsReadService(readRepository, unitRepository, profileRepository,
                yearRepository, gradeRepository, classroomRepository, students, subjectRepository, allocationRepository);

        var response = small.operations("tenant-a", "school-a", true, true, true, true, Set.of("students", "subjects"));

        assertThat(response.subjects()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("sub-ic3");
            assertThat(item.studentCount()).isEqualTo(1);
        });
        assertThat(response.students()).extracting(item -> item.id() + ":" + item.subjectIds())
                .containsExactly("s1:[sub-ic3]", "s2:[]");
    }
}
