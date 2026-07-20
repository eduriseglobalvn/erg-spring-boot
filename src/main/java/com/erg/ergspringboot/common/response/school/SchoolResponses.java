package com.erg.ergspringboot.common.response.school;

import java.time.LocalDate;
import java.util.List;

public final class SchoolResponses {
    private SchoolResponses() {}

    public record SchoolResponse(
            String id, String name, String slug, String schoolType, String status,
            String address, String district, String principalName, String contactPhone,
            String contactEmail, long version, AcademicYearResponse currentAcademicYear,
            List<GradeResponse> grades
    ) {}

    public record AcademicYearResponse(
            String id, int startYear, int endYear, String displayName, String status,
            LocalDate startsOn, LocalDate endsOn, long version
    ) {}

    public record GradeResponse(
            String id, String academicYearId, String gradeCode, String label,
            int sortOrder, String status, String source
    ) {}

    public record CommandResult(String id, String status, long version) {}

    public record GradeSyncResponse(String academicYearId, int created, int existing, List<GradeResponse> grades) {}
}
