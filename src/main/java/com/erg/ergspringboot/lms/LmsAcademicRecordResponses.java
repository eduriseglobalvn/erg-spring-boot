package com.erg.ergspringboot.lms;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class LmsAcademicRecordResponses {
    private LmsAcademicRecordResponses() { }

    public record AttendanceWorkspaceResponse(
            String tenantId,
            String classId,
            String subjectId,
            String from,
            String to,
            List<AttendanceRecordResponse> records
    ) { }

    public record AttendanceRecordResponse(
            String id,
            String studentId,
            String subjectId,
            String attendanceDate,
            int lessonIndex,
            String status,
            int version,
            OffsetDateTime updatedAt
    ) { }

    public record GradebookWorkspaceResponse(
            String tenantId,
            String classId,
            String subjectId,
            List<GradebookColumnResponse> columns,
            List<GradebookScoreResponse> scores
    ) { }

    public record GradebookColumnResponse(
            String id,
            String label,
            BigDecimal maxScore,
            String kind,
            int position,
            int version,
            OffsetDateTime updatedAt
    ) { }

    public record GradebookScoreResponse(
            String studentId,
            String columnId,
            BigDecimal score,
            BigDecimal maxScore,
            int version,
            OffsetDateTime updatedAt
    ) { }
}
