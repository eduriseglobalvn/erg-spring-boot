package com.erg.ergspringboot.common.request.lms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** Typed command payloads for the Admin LMS operational directory. */
public final class LmsOperationalRequests {
    private LmsOperationalRequests() {}

    public record CreateClassRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 64) String grade,
            @Size(max = 64) String academicYear,
            @Size(max = 36) String homeroomTeacherId,
            @Size(max = 32) String status
    ) {}

    public record UpdateClassRequest(
            Long version,
            @Size(max = 255) String name,
            @Size(max = 64) String grade,
            @Size(max = 64) String academicYear,
            @Size(max = 36) String homeroomTeacherId,
            @Size(max = 32) String status
    ) {}

    public record CreateStudentRequest(
            @NotBlank @Size(max = 255) String studentCode,
            @NotBlank @Size(max = 255) String fullName,
            String academicClassId,
            @Email @Size(max = 255) String email,
            @Size(max = 255) String username,
            @Size(max = 32) String gender,
            OffsetDateTime birthday,
            @Size(max = 64) String phone,
            @Size(max = 1000) String address,
            @Size(max = 255) String parentName,
            @Size(max = 64) String parentPhone,
            @Email @Size(max = 255) String parentEmail,
            @Size(max = 64) String parentRelationship,
            OffsetDateTime enrollmentDate,
            @Size(max = 1000) String note,
            @Size(max = 32) String status
    ) {}

    public record BulkStudentImportRequest(
            @NotEmpty @Size(max = 500) List<@Valid CreateStudentRequest> items
    ) {}

    public record UpdateStudentRequest(
            Long version,
            @Size(max = 255) String studentCode,
            @Size(max = 255) String fullName,
            String academicClassId,
            @Email @Size(max = 255) String email,
            @Size(max = 255) String username,
            @Size(max = 32) String gender,
            OffsetDateTime birthday,
            @Size(max = 64) String phone,
            @Size(max = 1000) String address,
            @Size(max = 255) String parentName,
            @Size(max = 64) String parentPhone,
            @Email @Size(max = 255) String parentEmail,
            @Size(max = 64) String parentRelationship,
            OffsetDateTime enrollmentDate,
            @Size(max = 1000) String note,
            @Size(max = 32) String status
    ) {}

    public record CreateSubjectRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 255) String code,
            @Size(max = 36) String schoolId,
            @Size(max = 32) String status
    ) {}

    public record UpdateSubjectRequest(
            Long version,
            @Size(max = 255) String name,
            @Size(max = 255) String code,
            @Size(max = 32) String status
    ) {}

    public record EnrollmentItem(
            @NotBlank String studentId,
            @NotBlank String classId,
            @Size(max = 64) String academicYear
    ) {}

    public record EnrollStudentRequest(@Valid @NotBlank String studentId,
                                       @NotBlank String classId,
                                       @Size(max = 64) String academicYear) {}

    public record BulkEnrollRequest(
            @NotEmpty @Size(max = 500) List<@Valid EnrollmentItem> items
    ) {}
}
