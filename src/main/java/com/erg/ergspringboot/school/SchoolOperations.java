package com.erg.ergspringboot.school;

import static com.erg.ergspringboot.common.request.school.SchoolRequests.*;
import static com.erg.ergspringboot.common.response.school.SchoolResponses.*;

import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.school.SchoolCommandService;
import org.springframework.stereotype.Service;

/** Public command boundary for the school bounded context. */
@Service
public class SchoolOperations {
    private final SchoolCommandService commands;

    public SchoolOperations(SchoolCommandService commands) {
        this.commands = commands;
    }

    public SchoolResponse create(CreateSchoolRequest request, UserAccessContext access) {
        return commands.create(request, access);
    }

    public SchoolResponse update(String schoolId, UpdateSchoolRequest request, UserAccessContext access) {
        return commands.update(schoolId, request, access);
    }

    public CommandResult archive(String schoolId, UserAccessContext access) {
        return commands.archive(schoolId, access);
    }

    public CommandResult restore(String schoolId, UserAccessContext access) {
        return commands.restore(schoolId, access);
    }

    public AcademicYearResponse createAcademicYear(String schoolId, CreateAcademicYearRequest request, UserAccessContext access) {
        return commands.createAcademicYear(schoolId, request, access);
    }

    public AcademicYearResponse activateAcademicYear(String schoolId, String yearId, VersionRequest request, UserAccessContext access) {
        return commands.activateAcademicYear(schoolId, yearId, request, access);
    }

    public AcademicYearResponse closeAcademicYear(String schoolId, String yearId, VersionRequest request, UserAccessContext access) {
        return commands.closeAcademicYear(schoolId, yearId, request, access);
    }

    public GradeSyncResponse syncDefaultGrades(String schoolId, UserAccessContext access) {
        return commands.syncDefaultGrades(schoolId, access);
    }

    public GradeResponse createGrade(String schoolId, CreateGradeRequest request, UserAccessContext access) {
        return commands.createGrade(schoolId, request, access);
    }

    public GradeResponse updateGrade(String schoolId, String gradeId, UpdateGradeRequest request, UserAccessContext access) {
        return commands.updateGrade(schoolId, gradeId, request, access);
    }
}
