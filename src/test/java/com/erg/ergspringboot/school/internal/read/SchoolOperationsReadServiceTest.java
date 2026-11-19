package com.erg.ergspringboot.school.internal.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.projection.SchoolStudentStatsProjection;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SchoolOperationsReadServiceTest {
    @Mock private SchoolOperationsReadRepository readRepository;
    @Mock private EducationUnitRepository unitRepository;
    @Mock private SchoolOperationalProfileRepository profileRepository;
    @Mock private SchoolAcademicYearRepository yearRepository;
    @Mock private SchoolGradeRepository gradeRepository;
    @Mock private ClassroomRepository classroomRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private CurriculumSubjectRepository subjectRepository;
    @Mock private CurriculumClassSubjectAllocationRepository allocationRepository;
    private SchoolOperationsReadService service;
    private SchoolOperationalProfile profile;

    @BeforeEach
    void setUp() {
        service = new SchoolOperationsReadService(readRepository, unitRepository, profileRepository,
                yearRepository, gradeRepository, classroomRepository, studentRepository, subjectRepository, allocationRepository);
        EducationUnit unit = new EducationUnit();
        unit.setId("school-a");
        unit.setTenantId("tenant-a");
        profile = new SchoolOperationalProfile();
        profile.setSchoolId("school-a");
        profile.setTenantId("tenant-a");
        lenient().when(unitRepository.findByIdAndTenantId("school-a", "tenant-a")).thenReturn(Optional.of(unit));
        lenient().when(profileRepository.findBySchoolIdAndTenantId("school-a", "tenant-a")).thenReturn(Optional.of(profile));
        lenient().when(studentRepository.summarizeSchoolStudents(anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(new SchoolStudentStatsProjection(100_000L, 7L, 90_000L, 99_000L, 30L));
        lenient().when(studentRepository.scoreSchoolStudents("tenant-a", "school-a"))
                .thenReturn(List.<Object[]>of(new Object[] {40L, 7.25}));
    }

    @Test
    void summaryKeepsGradeCountWhenGradeRowsAreNotSelected() {
        profile.setCurrentAcademicYearId("year-a");
        when(gradeRepository.countByTenantIdAndSchoolIdAndAcademicYearId("tenant-a", "school-a", "year-a"))
                .thenReturn(12L);

        var response = service.operations("tenant-a", "school-a", true, true, true, true, Set.of());

        assertThat(response.summary().gradeCount()).isEqualTo(12);
        assertThat(response.grades()).isEmpty();
    }

    @Test
    void workspaceCountsStudentsInSqlWithoutLoadingThem() {
        CurriculumClassSubjectAllocation allocation = new CurriculumClassSubjectAllocation();
        allocation.setAcademicClassId("class-1");
        allocation.setSubjectId("sub-ic3");
        when(allocationRepository.findByTenantIdAndSchoolIdOrderByAcademicClassIdAscSubjectIdAsc("tenant-a", "school-a"))
                .thenReturn(List.of(allocation));
        CurriculumSubject subject = new CurriculumSubject();
        subject.setId("sub-ic3");
        subject.setTenantId("tenant-a");
        subject.setName("IC3");
        when(subjectRepository.findAllById(List.of("sub-ic3"))).thenReturn(List.of(subject));
        when(studentRepository.countSchoolStudentsByClass("tenant-a", "school-a"))
                .thenReturn(List.of(new Object[] {"class-1", 30L}, new Object[] {"class-2", 5L}));

        var response = service.operations("tenant-a", "school-a", true, true, true, true, Set.of("students", "subjects"));

        assertThat(response.summary().studentCount()).isEqualTo(100_000);
        assertThat(response.summary().students().atRisk()).isEqualTo(7);
        assertThat(response.summary().scoredStudentCount()).isEqualTo(40);
        assertThat(response.summary().averageScore()).isEqualTo(7.25);
        assertThat(response.subjects()).singleElement().satisfies(item -> assertThat(item.studentCount()).isEqualTo(30));
        assertThat(response.students()).isEmpty();
        verify(studentRepository).summarizeSchoolStudents("tenant-a", "school-a", null, null, null, null, Set.of("class-1"));
        verify(studentRepository, never()).findSchoolStudents(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void studentPageClampsSizeNormalizesFiltersAndGivesStudentsTheirClassSubjects() {
        CurriculumClassSubjectAllocation allocation = new CurriculumClassSubjectAllocation();
        allocation.setAcademicClassId("class-1");
        allocation.setSubjectId("sub-ic3");
        when(allocationRepository.findByTenantIdAndSchoolIdOrderByAcademicClassIdAscSubjectIdAsc("tenant-a", "school-a"))
                .thenReturn(List.of(allocation, allocation));
        Student inClass = new Student();
        inClass.setId("s1");
        inClass.setAcademicClassId("class-1");
        Student noClass = new Student();
        noClass.setId("s2");
        when(studentRepository.findSchoolStudents("tenant-a", "school-a", "%an%", "6", null, "at-risk", PageRequest.of(2, 200)))
                .thenReturn(new PageImpl<>(List.of(inClass, noClass), PageRequest.of(2, 200), 1_000));
        Classroom classroom = new Classroom();
        classroom.setId("class-1");
        classroom.setTenantId("tenant-a");
        classroom.setName("6A");
        classroom.setGrade("6");
        when(classroomRepository.findAllById(List.of("class-1"))).thenReturn(List.of(classroom));

        var page = service.students("tenant-a", "school-a", "  An ", " 6 ", " ", "AT-RISK", 2, 5_000);

        assertThat(page.size()).isEqualTo(200);
        assertThat(page.totalItems()).isEqualTo(1_000);
        assertThat(page.stats().total()).isEqualTo(100_000);
        assertThat(page.items()).extracting(item -> item.id() + ":" + item.className() + ":" + item.subjectIds())
                .containsExactly("s1:6A:[sub-ic3]", "s2:null:[]");
        verify(studentRepository).summarizeSchoolStudents(eq("tenant-a"), eq("school-a"), eq("%an%"), eq("6"), isNull(),
                eq("at-risk"), eq(Set.of("class-1")));
    }
}
