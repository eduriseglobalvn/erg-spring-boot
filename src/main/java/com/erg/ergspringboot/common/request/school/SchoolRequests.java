package com.erg.ergspringboot.common.request.school;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class SchoolRequests {
    private SchoolRequests() {}

    public record CreateSchoolRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank String schoolType,
            @NotNull @Min(2000) @Max(2200) Integer academicYearStart,
            @Size(max = 500) String address,
            @Size(max = 255) String district,
            @Size(max = 255) String principalName,
            @Size(max = 50) String contactPhone,
            @Email @Size(max = 255) String contactEmail,
            String status
    ) {}

    public record UpdateSchoolRequest(
            @NotNull Long version,
            @Size(max = 255) String name,
            String schoolType,
            @Size(max = 500) String address,
            @Size(max = 255) String district,
            @Size(max = 255) String principalName,
            @Size(max = 50) String contactPhone,
            @Email @Size(max = 255) String contactEmail,
            String status
    ) {}

    public record CreateAcademicYearRequest(
            @NotNull @Min(2000) @Max(2200) Integer startYear,
            LocalDate startsOn,
            LocalDate endsOn,
            Boolean activate
    ) {}

    public record VersionRequest(@NotNull Long version) {}

    public record CreateGradeRequest(
            @NotBlank @Size(max = 32) String gradeCode,
            @NotBlank @Size(max = 100) String label,
            @NotNull Integer sortOrder,
            String status
    ) {}

    public record UpdateGradeRequest(
            @Size(max = 100) String label,
            Integer sortOrder,
            String status
    ) {}
}
