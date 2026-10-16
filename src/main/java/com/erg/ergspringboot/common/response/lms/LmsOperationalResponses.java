package com.erg.ergspringboot.common.response.lms;

import java.time.OffsetDateTime;
import java.util.List;

/** Stable response shapes for operational directory mutations. */
public final class LmsOperationalResponses {
    private LmsOperationalResponses() {}

    public record ClassResponse(String id, String tenantId, String schoolId, String name, String grade,
                                String academicYear, String homeroomTeacherId, String status,
                                long version, OffsetDateTime updatedAt) {}

    public record StudentResponse(String id, String tenantId, String schoolId, String academicClassId,
                                   String studentCode, String fullName, String username, String authUserId,
                                   String email, String gender, OffsetDateTime birthday, String phone,
                                   String address, String parentName, String parentPhone, String parentEmail,
                                   String parentRelationship, OffsetDateTime enrollmentDate, String note,
                                   String status, long version, OffsetDateTime updatedAt) {}

    public record SubjectResponse(String id, String tenantId, String schoolId, String name, String code,
                                  String status, long version, OffsetDateTime updatedAt) {}

    public record EnrollmentResponse(String id, String tenantId, String schoolId, String studentId,
                                     String classId, String academicYear, String status, long version,
                                     boolean replayed, OffsetDateTime updatedAt) {}

    public record BulkEnrollmentResponse(String idempotencyKey, int requested, int enrolled,
                                         int replayed, List<EnrollmentResponse> items) {}

    public record BulkStudentImportResponse(String idempotencyKey, int requested, int created,
                                            int replayed, List<StudentResponse> items) {}
}
