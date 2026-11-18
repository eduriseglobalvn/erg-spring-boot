package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.util.Objects;

/**
 * Tenant- and school-scoped lookups plus school authorization for {@link LmsOperationalCommandService}.
 * Every lookup is keyed by the authenticated tenant from {@link UserAccessContext}.
 */
final class LmsOperationalScopeGuard {

    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final EducationUnitRepository schools;

    LmsOperationalScopeGuard(ClassroomRepository classrooms, StudentRepository students,
                             EducationUnitRepository schools) {
        this.classrooms = classrooms;
        this.students = students;
        this.schools = schools;
    }

    Classroom scopedClass(UserAccessContext access, String classId, String schoolId) {
        Classroom classroom = classroom(access, classId);
        if (!Objects.equals(classroom.getSchoolId(), schoolId)) throw new BadRequestException("Class does not belong to the requested school.");
        assertSchool(access, schoolId);
        return classroom;
    }

    Classroom classroom(UserAccessContext access, String id) {
        return classrooms.findByTenantIdAndId(access.tenantId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found."));
    }

    Student student(UserAccessContext access, String id) {
        return students.findByTenantIdAndId(access.tenantId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
    }

    EducationUnit school(UserAccessContext access, String id) {
        return schools.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found."));
    }

    void assertSchool(UserAccessContext access, String schoolId) {
        if (schoolId == null || schoolId.isBlank() || !access.canAccessEducationUnit(schoolId)) {
            throw new ForbiddenException("School scope is not allowed for the authenticated principal.");
        }
    }
}
