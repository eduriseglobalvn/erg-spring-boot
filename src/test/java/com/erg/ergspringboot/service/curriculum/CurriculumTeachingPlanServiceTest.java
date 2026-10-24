package com.erg.ergspringboot.service.curriculum;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.curriculum.CurriculumTeachingPlanSetting;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumTeachingPlanSettingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurriculumTeachingPlanServiceTest {

    @Mock
    CurriculumTeachingPlanSettingRepository settings;

    @Test
    void rejectsStaleClientVersionBeforeOverwritingTeachingPlan() {
        CurriculumTeachingPlanSetting existing = new CurriculumTeachingPlanSetting();
        existing.setId("plan-1");
        existing.setTenantId("tenant-1");
        existing.setSchoolId("school-1");
        existing.setSubjectId("subject-1");
        existing.setAcademicYear("2026-2027");
        existing.setRecordVersion(4L);
        when(settings.findByTenantIdAndSchoolIdAndSubjectIdAndAcademicYear(
                "tenant-1", "school-1", "subject-1", "2026-2027")).thenReturn(Optional.of(existing));

        CurriculumTeachingPlanService service = new CurriculumTeachingPlanService(settings);
        UserAccessContext access = new UserAccessContext(
                "user-1", "tenant-1", List.of("school-1"), List.of(), "school-1", null);
        var request = new CurriculumDtos.CurriculumTeachingPlanRequest(
                LocalDate.of(2026, 9, 1), List.of(2, 4, 6), 2,
                "Asia/Ho_Chi_Minh", "active", 3L);

        assertThatThrownBy(() -> service.upsert("school-1", "subject-1", "2026-2027", request, access, false))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("reload before saving");
        verify(settings, never()).save(existing);
    }
}
