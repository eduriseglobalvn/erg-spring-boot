package com.erg.ergspringboot.lms.internal.classroom;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsClassroomService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

/** Tenant/scoped classroom read queries used by the LMS module. */
@Service
public class LmsClassroomQueryUseCase {
    private final LmsClassroomService classrooms;

    public LmsClassroomQueryUseCase(LmsClassroomService classrooms) { this.classrooms = classrooms; }

    public EducationUnitListResponse listEducationUnits(UserAccessContext access) {
        List<String> schoolIds = classrooms.findDistinctSchoolIdsByTenant(tenant(access.tenantId()));
        if (schoolIds == null) {
            schoolIds = classrooms.findByTenantIdOrderByNameAsc(tenant(access.tenantId())).stream()
                    .map(Classroom::getSchoolId).filter(java.util.Objects::nonNull).distinct().toList();
        }
        List<EducationUnitItem> items = schoolIds.stream().filter(access::canAccessEducationUnit)
                .map(id -> new EducationUnitItem(id, id)).toList();
        return new EducationUnitListResponse(items);
    }

    @Cacheable(cacheNames = "lmsClassesBySchool", key = "{#access.tenantId(), #schoolId, #access.currentEducationUnitId(), #access.managedEducationUnitIds().toString()}")
    public ClassesBySchoolResponse listClassesBySchool(String schoolId, UserAccessContext access) {
        String resolved = schoolId == null || schoolId.isBlank() ? access.currentEducationUnitId() : schoolId.trim();
        if (resolved == null || resolved.isBlank()) throw new BadRequestException("schoolId is required");
        if (!access.canAccessEducationUnit(resolved)) throw new com.erg.ergspringboot.exception.ForbiddenException(
                "You can only access LMS data in managed education units/classes");
        List<ClassItem> items = classrooms.findByTenantIdAndSchoolIdOrderByNameAsc(tenant(access.tenantId()), resolved)
                .stream().filter(item -> access.canAccessClass(item.getId()))
                .map(this::classPayload).toList();
        return new ClassesBySchoolResponse(resolved, items, scope(access));
    }

    private ClassItem classPayload(Classroom c) { return new ClassItem(c.getId(), c.getName(), c.getSchoolId(), c.getGrade(), c.getAcademicYear()); }
    private AccessScopeResponse scope(UserAccessContext access) { return new AccessScopeResponse(access.tenantId(), access.currentEducationUnitId(), access.currentClassId(), access.managedEducationUnitIds(), access.managedClassIds()); }
    private String tenant(String value) { return value == null || value.isBlank() ? "erg" : value.trim(); }
}
