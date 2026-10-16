package com.erg.ergspringboot.service.curriculum;

import com.erg.ergspringboot.model.curriculum.CurriculumTeachingPlanSetting;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumTeachingPlanSettingRepository;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTeachingPlanRequest;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTeachingPlanResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurriculumTeachingPlanService {
    private final CurriculumTeachingPlanSettingRepository settings;

    public CurriculumTeachingPlanService(CurriculumTeachingPlanSettingRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public CurriculumTeachingPlanResponse get(String schoolId, String subjectId, String academicYear,
                                              UserAccessContext access, boolean allSchoolAccess) {
        requireScope(schoolId, access, allSchoolAccess);
        return settings.findByTenantIdAndSchoolIdAndSubjectIdAndAcademicYear(
                        access.tenantId(), schoolId, subjectId, academicYear)
                .map(CurriculumTeachingPlanService::toResponse)
                .orElse(null);
    }

    @Transactional
    public CurriculumTeachingPlanResponse upsert(String schoolId, String subjectId, String academicYear,
                                                  CurriculumTeachingPlanRequest request, UserAccessContext access,
                                                  boolean allSchoolAccess) {
        requireScope(schoolId, access, allSchoolAccess);
        if (request == null || request.startDate() == null) {
            throw CurriculumException.badRequest("CURRICULUM_START_DATE_REQUIRED", "startDate is required");
        }
        if (request.sessionsPerDay() == null || request.sessionsPerDay() < 1 || request.sessionsPerDay() > 8) {
            throw CurriculumException.badRequest("CURRICULUM_SESSIONS_INVALID", "sessionsPerDay must be between 1 and 8");
        }
        List<Integer> days = request.teachingDays() == null ? List.of() : request.teachingDays().stream().distinct().sorted().toList();
        if (days.isEmpty() || days.stream().anyMatch(day -> day < 0 || day > 6)) {
            throw CurriculumException.badRequest("CURRICULUM_TEACHING_DAYS_INVALID", "teachingDays must contain weekday numbers 0..6");
        }
        CurriculumTeachingPlanSetting setting = settings
                .findByTenantIdAndSchoolIdAndSubjectIdAndAcademicYear(access.tenantId(), schoolId, subjectId, academicYear)
                .orElseGet(CurriculumTeachingPlanSetting::new);
        if (setting.getId() != null && request.expectedVersion() != null
                && !request.expectedVersion().equals(setting.getRecordVersion())) {
            throw CurriculumException.conflict(
                    "CURRICULUM_TEACHING_PLAN_STALE",
                    "teaching plan changed; reload before saving");
        }
        setting.setTenantId(access.tenantId());
        setting.setSchoolId(schoolId);
        setting.setSubjectId(subjectId);
        setting.setAcademicYear(academicYear);
        setting.setStartDate(request.startDate());
        setting.setTeachingDays(days.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        setting.setSessionsPerDay(request.sessionsPerDay());
        setting.setTimezone(request.timezone() == null || request.timezone().isBlank() ? "Asia/Ho_Chi_Minh" : request.timezone());
        setting.setStatus(request.status() == null || request.status().isBlank() ? "active" : request.status());
        setting.setUpdatedBy(access.userId());
        if (setting.getCreatedBy() == null) setting.setCreatedBy(access.userId());
        return toResponse(settings.save(setting));
    }

    private void requireScope(String schoolId, UserAccessContext access, boolean allSchoolAccess) {
        if (schoolId == null || schoolId.isBlank() || (!allSchoolAccess && !access.canAccessEducationUnit(schoolId))) {
            throw CurriculumException.notFound("school", schoolId);
        }
    }

    private static CurriculumTeachingPlanResponse toResponse(CurriculumTeachingPlanSetting setting) {
        List<Integer> days = java.util.Arrays.stream(setting.getTeachingDays().split(","))
                .filter(value -> !value.isBlank()).map(Integer::parseInt).toList();
        return new CurriculumTeachingPlanResponse(setting.getId(), setting.getTenantId(), setting.getSchoolId(),
                setting.getSubjectId(), setting.getAcademicYear(), setting.getStartDate(), days,
                setting.getSessionsPerDay(), setting.getTimezone(), setting.getStatus(), setting.getRecordVersion(),
                setting.getCreatedAt(), setting.getUpdatedAt());
    }
}
