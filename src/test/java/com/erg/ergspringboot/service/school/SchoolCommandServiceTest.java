package com.erg.ergspringboot.service.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.school.SchoolRequests.CreateSchoolRequest;
import com.erg.ergspringboot.common.response.school.SchoolResponses.SchoolResponse;
import com.erg.ergspringboot.model.school.SchoolAcademicYear;
import com.erg.ergspringboot.model.school.SchoolGrade;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.repository.school.SchoolAcademicYearRepository;
import com.erg.ergspringboot.repository.school.SchoolGradeRepository;
import com.erg.ergspringboot.repository.school.SchoolOperationalProfileRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchoolCommandServiceTest {
    @Mock EducationUnitRepository unitRepository;
    @Mock SchoolOperationalProfileRepository profileRepository;
    @Mock SchoolAcademicYearRepository yearRepository;
    @Mock SchoolGradeRepository gradeRepository;
    @Mock ClassroomRepository classroomRepository;

    private SchoolCommandService service;
    private final UserAccessContext access = new UserAccessContext("user-1", "tenant-1", List.of("*"), List.of("*"), null, null);

    @BeforeEach
    void setUp() {
        service = new SchoolCommandService(unitRepository, profileRepository, yearRepository, gradeRepository, classroomRepository);
        when(gradeRepository.findByTenantIdAndSchoolIdAndAcademicYearIdOrderBySortOrderAsc(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void createsPrimarySchoolWithComputedEndYearAndFiveDefaultGrades() {
        when(gradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SchoolResponse response = service.create(request("PRIMARY", 2025), access);

        ArgumentCaptor<SchoolAcademicYear> yearCaptor = ArgumentCaptor.forClass(SchoolAcademicYear.class);
        verify(yearRepository).save(yearCaptor.capture());
        assertThat(yearCaptor.getValue().getStartYear()).isEqualTo(2025);
        assertThat(yearCaptor.getValue().getEndYear()).isEqualTo(2026);
        assertThat(yearCaptor.getValue().getStatus()).isEqualTo("CURRENT");

        ArgumentCaptor<SchoolGrade> gradeCaptor = ArgumentCaptor.forClass(SchoolGrade.class);
        verify(gradeRepository, org.mockito.Mockito.times(5)).save(gradeCaptor.capture());
        assertThat(gradeCaptor.getAllValues()).extracting(SchoolGrade::getGradeCode)
                .containsExactly("1", "2", "3", "4", "5");
        assertThat(response.currentAcademicYear().displayName()).isEqualTo("2025–2026");
        assertThat(response.id()).isNotBlank();
    }

    @Test
    void doesNotCreateGradesForUniversity() {
        SchoolResponse response = service.create(request("UNIVERSITY", 2026), access);

        verify(gradeRepository, never()).save(any());
        assertThat(response.grades()).isEmpty();
        assertThat(response.currentAcademicYear().endYear()).isEqualTo(2027);
    }

    private CreateSchoolRequest request(String type, int startYear) {
        return new CreateSchoolRequest("Trường kiểm thử", type, startYear, "1 Đường A", "Quận 1",
                "Hiệu trưởng", "0900000000", "school@example.com", "ONBOARDING");
    }
}
